package ar.edu.unc.fcefyn.pcp.tp1.solution;
import ar.edu.unc.fcefyn.pcp.tp1.solution.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
public class ValidationWorker implements Runnable {
    private final Order order;
    private final OutcomeDecider outcomeDecider;
    private final SimulationConfig simulationConfig;

    public ValidationWorker(Order order, OutcomeDecider outcomeDecider, SimulationConfig simulationConfig) {
        this.order = order;
        this.outcomeDecider = outcomeDecider;
        this.simulationConfig = simulationConfig;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(simulationConfig.getValidationTime());
            boolean isValid = outcomeDecider.isOrderValid(order);
            order.setValid(isValid);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

}
