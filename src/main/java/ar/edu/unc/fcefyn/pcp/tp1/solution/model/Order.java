package ar.edu.unc.fcefyn.pcp.tp1.solution.model;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;

public final class Order {

    //Datos fijos
    private final int id;

    //Datos mutables
    private String assignedPrinterId;  // id historico: se conserva aunque se libere
    private Printer reservedPrinter; // null si no tiene impresora ahora
    private OrderState state = OrderState.CREATED;
    private int assignmentCount;
    private int validationCount;
    private int printingCount;
    private int qualityControlCount;

    public Order(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public OrderState getState() {
        return state;
    }

    public Printer getReservedPrinter() {
        return reservedPrinter;
    }

    public String getAssignedPrinterId() {
        return assignedPrinterId;
    }

    //Etapa 1: CREATED -> WAITING_VALIDATION y asocia la impresora
    public void assign(Printer printer) {
        advance(Stage.ASSIGNMENT, OrderState.CREATED, OrderState.WAITING_VALIDATION);
        attachPrinter(printer);
    }

    //Etapa 2: WAITING_VALIDATION -> READY_TO_PRINT o REJECTED (si se rechaza, suelta la impresora)
    public void finishValidation(boolean valid) {
        if (valid) {
            advance(Stage.VALIDATION, OrderState.WAITING_VALIDATION, OrderState.READY_TO_PRINT);
        } else {
            advance(Stage.VALIDATION, OrderState.WAITING_VALIDATION, OrderState.REJECTED);
            detachPrinter();
        }
    }

    //Etapa 3: READY_TO_PRINT -> PRINTED o PRINT_FAILED (en ambos casos suelta la impresora)
    public void finishPrinting(boolean success) {
        OrderState to = success ? OrderState.PRINTED : OrderState.PRINT_FAILED;
        advance(Stage.PRINTING, OrderState.READY_TO_PRINT, to);
        detachPrinter();
    }
    //Etapa 4: PRINTED -> APPROVED o DEFECTIVE (no usa impresora)

    public void finishQualityControl(boolean approved) {
        OrderState to = approved ? OrderState.APPROVED : OrderState.DEFECTIVE;
        advance(Stage.QUALITY_CONTROL, OrderState.PRINTED, to);
    }

    private void advance(Stage stage, OrderState from, OrderState to) {
        if (state != from) {
            throw new IllegalStateException("La orden " + id + " deberia estar en " + from + " pero esta en " + state);
        }
        state = to;

        //Dependiendo el stage en el que estemos sumamos uno a su contador
        switch (stage) {
            case ASSIGNMENT ->
                assignmentCount++;
            case VALIDATION ->
                validationCount++;
            case PRINTING ->
                printingCount++;
            case QUALITY_CONTROL ->
                qualityControlCount++;
            case INITIALIZATION ->
                throw new IllegalArgumentException("INITIALIZATION no es una transicion");
        }
    }

    private void attachPrinter(Printer printer) {
        if (reservedPrinter != null) {
            throw new IllegalStateException("La orden " + id + " ya tiene la impresora " + reservedPrinter.getId() + " asociada ");
        }
        reservedPrinter = printer;
        assignedPrinterId = printer.getId();
    }

    private Printer detachPrinter() {
        if (reservedPrinter == null) {
            throw new IllegalStateException("La orden " + id + " no tiene ninguna impresora asociada ");
        }
        Printer printer = reservedPrinter; //Guardo la referencia para devolverla
        reservedPrinter = null; //Saco la impresora actual
        return printer; // Devuelvo cual fue la impresora
    }

    public OrderSnapshot snapshot() {
        return new OrderSnapshot(id, state, assignedPrinterId,
                assignmentCount, validationCount, printingCount, qualityControlCount);
    }

}
