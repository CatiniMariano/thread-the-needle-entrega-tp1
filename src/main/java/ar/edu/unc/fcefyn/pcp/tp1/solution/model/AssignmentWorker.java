package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.solution.model.Order;

public final class AssignmentWorker implements Runnable {


    private final SimulationMonitor monitor;
    private final SimulationConfig config;

    public AssignmentWorker(SimulationMonitor monitor, SimulationConfig config) {
        this.monitor = monitor;
        this.config = config;
    }

    @Override
    public void run() {
        try {
            while (true) {
               
                Order order = monitor.takeForAssignment();
                if (order == null) {
                    return; 
                }

                sleep(config.assignmentDelayMillis());

                // El monitor espera (wait) hasta que haya una impresora AVAILABLE, la reserva,
                // cambia la orden a WAITING_VALIDATION, registra el evento y hace notifyAll,
                // todo bajo el mismo lock.
                boolean assigned = monitor.assignPrinter(order);
                if (!assigned) {
                    // false = la simulación se canceló mientras esperaba una impresora.
                    // Esta orden no recibió reserva, así que no hay nada más que hacer.
                    return;
                }
            }
        } catch (InterruptedException error) {
            
            Thread.currentThread().interrupt();
        } catch (RuntimeException error) {
            
            monitor.cancel(error);
        }
    }

    private static void sleep(long millis) throws InterruptedException {
        if (millis > 0) {
            Thread.sleep(millis);
        }
    }
}