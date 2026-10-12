package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Clase encargada de escribir en disco los resultados de la simulación.
 * Genera dos archivos: elementos.csv y resumen.properties.
 */
public final class ResultWriter {

    private final Path outputDirectory;

    public ResultWriter(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Punto de entrada principal para escribir los resultados.
     */
    public void write(SimulationResult result) throws IOException {
        // Aseguramos que la carpeta de destino exista en el disco antes de escribir
        Files.createDirectories(outputDirectory);

        // Generamos cada archivo por separado
        writeCsv(result);
        writeProperties(result);
    }

    /**
     * Genera el archivo elementos.csv con el detalle de las órdenes ordenadas por ID.
     */
    private void writeCsv(SimulationResult result) throws IOException {
        Path csvPath = outputDirectory.resolve("elementos.csv");

        // 1. Copiamos las órdenes a un ArrayList para poder ordenarlas a mano
        List<OrderSnapshot> ordenes = new ArrayList<>(result.orders());

        // 2. Las ordenamos por ID de menor a mayor
        ordenes.sort((o1, o2) -> Integer.compare(o1.id(), o2.id()));

        // 3. Abrimos el archivo en UTF-8 usando try-with-resources
        try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
            
            // Escribimos la cabecera exacta que pide el TP
            writer.write("orderId;finalState;printer;assignmentCount;validationCount;printingCount;qualityControlCount");
            writer.newLine();

            // Recorremos la lista ordenada fila por fila
            for (OrderSnapshot order : ordenes) {
                
                // Si la impresora es null (ej: orden rechazada), dejamos una cadena vacía
                String impresora = "";
                if (order.assignedPrinterId() != null) {
                    impresora = String.valueOf(order.assignedPrinterId());
                }

                // Armamos la línea concatenando los campos separados por punto y coma
                String fila = order.id() + ";" +
                             order.state() + ";" +
                             impresora + ";" +
                             order.assignmentCount() + ";" +
                             order.validationCount() + ";" +
                             order.printingCount() + ";" +
                             order.qualityControlCount();

                writer.write(fila);
                writer.newLine();
            }
        }
    }

    /**
     * Genera el archivo resumen.properties con las métricas globales.
     */
    private void writeProperties(SimulationResult result) throws IOException {
        Path propertiesPath = outputDirectory.resolve("resumen.properties");
        Map<OrderState, Integer> totales = result.totalsByState();

        // Contamos cuántas órdenes quedaron a mitad de camino (estado NO terminal)
        int ordenesIntermedias = 0;
        for (OrderSnapshot order : result.orders()) {
            if (!order.state().isTerminal()) {
                ordenesIntermedias++;
            }
        }

        // Escribimos las claves línea por línea en el orden solicitado
        try (BufferedWriter writer = Files.newBufferedWriter(propertiesPath, StandardCharsets.UTF_8)) {
            writer.write("totalOrders=" + result.totalOrders());
            writer.newLine();

            writer.write("processedOrders=" + result.processedOrders());
            writer.newLine();

            writer.write("approvedOrders=" + totales.getOrDefault(OrderState.APPROVED, 0));
            writer.newLine();

            writer.write("rejectedOrders=" + totales.getOrDefault(OrderState.REJECTED, 0));
            writer.newLine();

            writer.write("printFailedOrders=" + totales.getOrDefault(OrderState.PRINT_FAILED, 0));
            writer.newLine();

            writer.write("defectiveOrders=" + totales.getOrDefault(OrderState.DEFECTIVE, 0));
            writer.newLine();

            writer.write("durationMillis=" + result.durationMillis());
            writer.newLine();

            writer.write("allThreadsTerminated=" + result.allThreadsTerminated());
            writer.newLine();

            writer.write("remainingIntermediateOrders=" + ordenesIntermedias);
            writer.newLine();
        }
    }
}