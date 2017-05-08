package com.outbrain.swinfra.metrics.exporter.text;

import com.outbrain.swinfra.metrics.Metric;
import com.outbrain.swinfra.metrics.MetricCollector;
import com.outbrain.swinfra.metrics.exporter.CollectorExporter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TextFormatter implements CollectorExporter {
    public static final String CONTENT_TYPE_004 = "text/plain; version=0.0.4; charset=utf-8";

    private final MetricCollector metricCollector;
    private final Map<Metric, String> headerByMetric = new ConcurrentHashMap<>();

    public TextFormatter(final MetricCollector metricCollector) {
        this.metricCollector = metricCollector;
    }


    @Override
    public void exportTo(final OutputStream outputStream) throws IOException {
        final Writer stream = new OutputStreamWriter(outputStream);
        final Map<String, String> staticLabels = metricCollector.getStaticLabels();
        for (final Metric metric : metricCollector) {
            final String header = headerByMetric.computeIfAbsent(metric, this::createHeader);
            stream.append(header);
            final List<String> labelNames = metric.getLabelNames();

            metric.forEachSample((sample) -> {
                try {
                    stream.append(sample.getName());
                    final String extraLabelName = sample.getExtraLabelName();
                    if (containsLabels(staticLabels, labelNames, extraLabelName)) {
                        stream.append("{");

                        for (final Map.Entry<String, String> entry : staticLabels.entrySet()) {
                            appendLabel(stream, entry.getKey(), entry.getValue());
                        }

                        final List<String> labelValues = sample.getLabelValues();
                        for (int i = 0; i < labelNames.size(); ++i) {
                            appendLabel(stream, labelNames.get(i), labelValues.get(i));
                        }

                        if (extraLabelName != null) {
                            appendLabel(stream, extraLabelName, sample.getExtraLabelValue());
                        }

                        stream.append("}");
                    }
                    stream.append(" ").append(doubleToGoString(sample.getValue())).append("\n");
                } catch (final IOException e) {
                    throw new RuntimeException("failed appending to output stream");
                }
            });
        }
        stream.flush();
    }

    private void appendLabel(final Appendable appendable, final String name, final String value) throws IOException {
        appendable.append(name).append("=\"").append(escapeLabelValue(value)).append("\",");
    }

    private boolean containsLabels(final Map<String, String> staticLabels, final List<String> labelNames, final String additionalLabelName) {
        return !staticLabels.isEmpty() || !labelNames.isEmpty() || additionalLabelName != null;
    }

    private String createHeader(final Metric metric) {
        return "# HELP " + metric.getName() + " " + escapeHelp(metric.getHelp()) + "\n" +
               "# TYPE " + metric.getName() + " " + metric.getType().getName() + "\n";
    }

    private static String escapeHelp(final String help) {
        return help.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private static String escapeLabelValue(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    /**
     * Convert a double to it's string representation in Go.
     */
    private static String doubleToGoString(final double value) {
        if (value == Double.POSITIVE_INFINITY) {
            return "+Inf";
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return "-Inf";
        }
        if (Double.isNaN(value)) {
            return "NaN";
        }
        return Double.toString(value);
    }
}