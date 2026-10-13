package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.solution.model.Order;

public final class ValidationWorker implements Runnable {

    private final SimulationMonitor monitor;
    private final SimulationConfig config;

    public ValidationWorker(SimulationMonitor monitor, SimulationConfig config) {
        this.monitor = monitor;
        this.config = config;
    }

    @Override
    public void run() {
        try {
            while (true) {
                // Espera dentro del monitor (wait) hasta que haya una orden en WAITING_VALIDATION
                Order order = monitor.takeForValidation();
                if (order == null) {
                    return; 
                }


                sleep(config.validationDelayMillis());

                // Funcion determinista, no necesita lock
                boolean valid = OutcomeDecider.isModelValid(order.getId(), config);

                monitor.completeValidation(order, valid);
            }
            } catch (InterruptedException e) {
            
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