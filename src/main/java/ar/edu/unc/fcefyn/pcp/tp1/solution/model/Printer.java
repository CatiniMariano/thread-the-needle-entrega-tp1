package ar.edu.unc.fcefyn.pcp.tp1.solution.model;

import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;

public final class Printer {
    //Datos fijos
    private final String id;
    private final int row;
    private final int column;

    //Datos mutables
    private PrinterState state = PrinterState.AVAILABLE;
    private int usageCount;
    private Order assignedOrder; // null si no tiene ninguna orden asignada

    public Printer(int row, int column){
        this.row = row;
        this.column = column;
        this.id = "P-" + row + "-" + column;
    }

    public String getId(){
        return id;
    }

    public int getRow(){
        return row;
    }

    public int getColumn(){
        return column;
    }

    public PrinterState getState(){
        return state;
    }

    public Order getAssignedOrder(){
        return assignedOrder;
    }

    //Metodo para pasar de AVAILABLE a RESERVED
    public void reserve(Order order){
        if(state != PrinterState.AVAILABLE){
            throw new IllegalStateException("La impresora " + id + " no esta disponible: su estado es " + state);
        }
        state = PrinterState.RESERVED;
        usageCount++;
        assignedOrder = order;
    }

    //Metodo para pasar de RESERVED a AVAILABLE
    public void release(Order order){
        requireReservedBy(order);
        state = PrinterState.AVAILABLE;
        assignedOrder = null;
    }

    //Metodo para pasar de RESERVED a OUT_OF_SERVICE
    public void disable(Order order){
        requireReservedBy(order);
        state = PrinterState.OUT_OF_SERVICE;
        assignedOrder = null;
    }

    public PrinterSnapshot snapshot(){
        Integer orderId = (assignedOrder == null) ? null : assignedOrder.getId();
        return new PrinterSnapshot(id, state, usageCount, orderId);
    }

    //Valida que la impresora este reservada y que sea por ESTA orden
    private void requireReservedBy(Order order){
        if(state != PrinterState.RESERVED){
            throw new IllegalStateException("La impresora " + id + " no esta reservada: su estado es " + state);
        }
        if(assignedOrder != order){
            throw new IllegalStateException("La impresora " + id + " no esta reservada por la orden " + order.getId());
        }
    }
}