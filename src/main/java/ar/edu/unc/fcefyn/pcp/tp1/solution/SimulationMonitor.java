package ar.edu.unc.fcefyn.pcp.tp1.solution;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;

public final class SimulationMonitor {

    // 1. Datos compartidos e inicializacion
    private final SimulationConfig config;
    private final List<Order> orders;
    private final Printer[][] printers;

    // Colas FIFO: offer agrega al final y poll retira la primera orden.
    // Cada cola tiene su propio monitor.
    private final Queue<Order> assignmentQueue = new ArrayDeque<>();
    private final Queue<Order> validationQueue = new ArrayDeque<>();
    private final Queue<Order> printingQueue = new ArrayDeque<>();
    private final Queue<Order> qualityControlQueue = new ArrayDeque<>();

    private final Object printersLock = new Object();
    private final Object eventsLock = new Object(); // Estado de las ordenes y su registro en el CSV.
    private final Object completionLock = new Object();
    private final EventLogger eventLogger;

    private int processedOrders;
    private Throwable failure;
    private volatile boolean cancelled;
    private volatile boolean completed;

    // Los workers arrancan despues de construir e inicializar el monitor.
    public SimulationMonitor(SimulationConfig config, EventLogger eventLogger) throws IOException {
        this.config = config;
        this.eventLogger = eventLogger;
        this.printers = new Printer[config.printerRows()][config.printerColumns()];
        for (int row = 0; row < config.printerRows(); row++) {
            for (int column = 0; column < config.printerColumns(); column++) {
                printers[row][column] = new Printer("P-" + row + "-" + column, row, column);
            }
        }
        this.orders = new ArrayList<>(config.totalOrders());
        for (int id = 1; id <= config.totalOrders(); id++) {
            Order order = new Order(id);
            orders.add(order);
            assignmentQueue.offer(order);
            eventLogger.logCreation(order);
        }
    }

    // 2. Acceso a las colas
    public Order takeForAssignment() throws InterruptedException {
        return takeFrom(assignmentQueue);
    }
    public Order takeForValidation() throws InterruptedException {
        return takeFrom(validationQueue);
    }
    public Order takeForPrinting() throws InterruptedException {
        return takeFrom(printingQueue);
    }
    public Order takeForQualityControl() throws InterruptedException {
        return takeFrom(qualityControlQueue);
    }
    // El mismo metodo usa un monitor diferente segun la cola recibida.
    private Order takeFrom(Queue<Order> queue) throws InterruptedException {
        synchronized (queue) {
            while (queue.isEmpty() && !completed && !cancelled) {
                queue.wait();
            }
            if (cancelled || completed) {
                return null;
            }
            return queue.poll();
        }
    }
    // Se invoca despues de soltar los candados de impresoras y eventos.
    private void enqueue(Queue<Order> queue, Order order) {
        synchronized (queue) {
            if (!cancelled) {
                queue.offer(order);
                queue.notifyAll();
            }
        }
    }

    private static boolean isEmpty(Queue<Order> queue) {
        synchronized (queue) {
            return queue.isEmpty();
        }
    }
    // 3. Etapas: actualizar recursos y registrar el evento; luego encolar o finalizar.
    public boolean assignPrinter(Order order) throws InterruptedException {
        try {
            synchronized (printersLock) {
                if (cancelled) {
                    return false;
                }
                synchronized (eventsLock) {
                    requirePendingAssignment(order);
                }
                Printer printer = findAvailablePrinter();
                while (printer == null && !cancelled) {
                    // No se retiene ningun otro candado durante esta espera.
                    printersLock.wait();
                    printer = findAvailablePrinter();
                }
                if (cancelled) {
                    return false;
                }
                // Unico orden de anidamiento: impresoras -> eventos.
                synchronized (eventsLock) {
                    if (cancelled) {
                        return false;
                    }
                    requirePendingAssignment(order);
                    printer.reserve(order);
                    order.assign(printer);
                    log(order, Stage.ASSIGNMENT, OrderState.CREATED, OrderState.WAITING_VALIDATION);
                }
            }
        } catch (UncheckedIOException error) {
            // Despertar otras colas solo cuando ya se soltaron los candados anteriores.
            cancel(error);
            throw error;
        }
        enqueue(validationQueue, order);
        return !cancelled;
    }

    public void completeValidation(Order order, boolean valid) {
        try {
            synchronized (printersLock) {
                synchronized (eventsLock) {
                    if (cancelled) {
                        return;
                    }
                    Printer printer = requireAssignedPrinter(order);
                    order.finishValidation(valid);
                    if (!valid) {
                        printer.release(order);
                    }
                    log(order, Stage.VALIDATION, OrderState.WAITING_VALIDATION,
                            valid ? OrderState.READY_TO_PRINT : OrderState.REJECTED);
                }
                if (!valid) {
                    printersLock.notifyAll();
                }
            }
        } catch (UncheckedIOException error) {
            cancel(error);
            throw error;
        }

        if (valid) {
            enqueue(printingQueue, order);
        } else {
            registerProcessedOrder();
        }
    }

    public void completePrinting(Order order, boolean successful) {
        try {
            synchronized (printersLock) {
                synchronized (eventsLock) {
                    if (cancelled) {
                        return;
                    }
                    Printer printer = requireAssignedPrinter(order);
                    order.finishPrinting(successful);
                    if (successful) {
                        printer.release(order);
                    } else {
                        printer.disable(order);
                    }
                    log(order, Stage.PRINTING, OrderState.READY_TO_PRINT,
                            successful ? OrderState.PRINTED : OrderState.PRINT_FAILED);
                }
                printersLock.notifyAll();
            }
        } catch (UncheckedIOException error) {
            cancel(error);
            throw error;
        }

        if (successful) {
            enqueue(qualityControlQueue, order);
        } else {
            registerProcessedOrder();
        }
    }

    public void completeQualityControl(Order order, boolean approved) {
        try {
            synchronized (eventsLock) {
                if (cancelled) {
                    return;
                }
                order.finishQualityControl(approved);
                log(order, Stage.QUALITY_CONTROL, OrderState.PRINTED,
                        approved ? OrderState.APPROVED : OrderState.DEFECTIVE);
            }
        } catch (UncheckedIOException error) {
            cancel(error);
            throw error;
        }
        registerProcessedOrder();
    }
    // 4. Finalizacion y cancelacion
    // La impresora ya se libero y el evento terminal ya se registro al contar la orden.
    private void registerProcessedOrder() {
        synchronized (completionLock) {
            if (cancelled) {
                return;
            }
            processedOrders++;
            if (processedOrders != config.totalOrders()) {
                return;
            }
            completed = true;
            completionLock.notifyAll();
        }
        wakeAllWaiters();
    }

    public void cancel(Throwable cause) {
        synchronized (completionLock) {
            if (failure == null) {
                failure = cause;
            }
            cancelled = true;
            completionLock.notifyAll();
        }
        wakeAllWaiters();
    }

    public void awaitCompletion() throws InterruptedException {
        synchronized (completionLock) {
            while (!completed && !cancelled) {
                completionLock.wait();
            }
        }
        if (!cancelled && (!isEmpty(assignmentQueue) || !isEmpty(validationQueue)
                || !isEmpty(printingQueue) || !isEmpty(qualityControlQueue))) {
            cancel(new IllegalStateException("La simulacion termino con ordenes en las colas"));
        }
    }
    // Se llama sin otros candados tomados. Se adquiere y libera uno por vez.
    private void wakeAllWaiters() {
        for (Object lock : List.of(assignmentQueue, validationQueue, printingQueue,
                qualityControlQueue, printersLock)) {
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }
    // Solo despues de cancelar y con todos los workers terminados.
    public void releaseReservationsAfterCancellation() {
        synchronized (printersLock) {
            for (Printer[] row : printers) {
                for (Printer printer : row) {
                    if (printer.getState() == PrinterState.RESERVED) {
                        printer.release(printer.getAssignedOrder());
                    }
                }
            }
        }
    }
    // 5. Consultas y resultados
    public Throwable getFailure() {
        synchronized (completionLock) {
            return failure;
        }
    }

    public int getProcessedOrders() {
        synchronized (completionLock) {
            return processedOrders;
        }
    }

    public int getRemainingIntermediateOrders() {
        synchronized (eventsLock) {
            int remaining = 0;
            for (Order order : orders) {
                if (!order.getState().isTerminal()) {
                    remaining++;
                }
            }
            return remaining;
        }
    }

    public List<OrderSnapshot> getOrderSnapshots() {
        synchronized (eventsLock) {
            List<OrderSnapshot> snapshots = new ArrayList<>(orders.size());
            for (Order order : orders) {
                snapshots.add(order.toSnapshot());
            }
            return snapshots;
        }
    }

    public List<PrinterSnapshot> getPrinterSnapshots() {
        synchronized (printersLock) {
            List<PrinterSnapshot> snapshots = new ArrayList<>();
            for (Printer[] row : printers) {
                for (Printer printer : row) {
                    snapshots.add(printer.toSnapshot());
                }
            }
            return snapshots;
        }
    }
    // 6. Auxiliares: busqueda, validaciones y registro de eventos
    // Llamado con printersLock tomado: buscar y reservar no pueden intercalarse.
    private Printer findAvailablePrinter() {
        for (Printer[] row : printers) {
            for (Printer printer : row) {
                if (printer.getState() == PrinterState.AVAILABLE) {
                    return printer;
                }
            }
        }
        return null;
    }
    // Llamado con eventsLock tomado.
    private void requirePendingAssignment(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("La orden no puede ser null");
        }
        if (order.getState() != OrderState.CREATED || order.getAssignedPrinter() != null) {
            throw new IllegalStateException(
                    "La orden " + order.getId() + " no esta pendiente de asignacion");
        }
    }
    // Llamado con printersLock y eventsLock tomados.
    private Printer requireAssignedPrinter(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("La orden no puede ser null");
        }
        Printer printer = order.getAssignedPrinter();
        if (printer == null || printer.getState() != PrinterState.RESERVED
                || printer.getAssignedOrder() != order) {
            throw new IllegalStateException(
                    "La orden " + order.getId() + " no tiene una reserva valida");
        }
        return printer;
    }
    // eventsLock protege tanto la transicion como su fila
    private void log(Order order, Stage stage, OrderState from, OrderState to) {
        try {
            eventLogger.logTransition(order, stage, from, to);
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo escribir eventos.csv", error);
        }
    }
}