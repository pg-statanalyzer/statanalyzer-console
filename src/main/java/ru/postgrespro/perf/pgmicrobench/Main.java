package ru.postgrespro.perf.pgmicrobench;

import org.apache.commons.cli.*;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.AnalysisResult;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.ModeReport;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.Sample;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.StatAnalyzer;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.console.Configuration;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.multimodality.LowlandModalityDetector;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.plotting.Plot;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;


public class Main {
    private static final String DEFMODALITY = "multi";
    private static final String DEFTEST = "ks";
    private static final String DEFESTIMATOR = "ks";
    private static final double DEFDIVISION = 1.0;
    private static final double DEFSENSITIVITY = 0.5;
    private static final double DEFPRECISION = 0.01;
    private static final boolean DEFVERBOSITY = false;
    private static final double DEFERROR = 1.0;

    private static Configuration config;

    public static void main(String[] args) {
        parseArgs(args);

        StatAnalyzer statAnalyzer = new StatAnalyzer(
                config.estimator.getEstimator(),
                config.criteria.getCriteria()
        );
        statAnalyzer.setModeDetector(new LowlandModalityDetector(config.sensitivity, config.precision, false));

        List<Double> dataList = new ArrayList<>(10000);
        try (Scanner scanner = new Scanner(new File(config.filename))) {
            while (scanner.hasNextDouble()) {
                dataList.add(scanner.nextDouble() / config.division);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        switch (config.modality) {
            case UNI -> {

            }

            case MULTI -> {
                AnalysisResult result = statAnalyzer.analyze(dataList);
                System.out.printf("Found modality: %d\n", result.getModeNumber());
                System.out.printf("Used parameter estimator: %s\n", config.estimator.getEstimator().getClass().getSimpleName());
                System.out.printf("Used test criteria: %s\n\n", config.criteria.getCriteria().getClass().getSimpleName());

                for (int i = 0; i < result.getModeNumber(); i++) {
                    ModeReport modeReport = result.getModeReports().get(i);

                    System.out.println("Mode #" + (i + 1));

                    if (config.error != 1.0 && modeReport.getBestDistribution().getPValue() < 1 - config.error) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("=== Mode Report (Summary) ===\n")
                                .append(String.format(Locale.US,
                                        "Mode bounds: [%.2f, %.2f]\n", modeReport.getLeftBound(), modeReport.getRightBound()))
                                .append(String.format(Locale.US,
                                        "Location: %.6f\n", modeReport.getLocation()))
                                .append(String.format(Locale.US,
                                        "Mode size: %d\n", modeReport.getSize()))
                                .append("COULD NOT FIT THE DISTRIBUTION WITH DESIRED ERROR\n");
                        System.out.println(sb.toString());
                        continue;
                    }

                    if (config.verbose) {
                        System.out.println(modeReport.toStringVerbose());
                    } else {
                        System.out.println(modeReport.toString());
                    }
                }

                Plot.plot(new Sample(dataList), result.getPdf(), "Summary pdf");
            }
        }
    }

    private static void parseArgs(String[] args) {
        Options opt = new Options();

        opt.addOption(Option.builder("h").longOpt("help")
                .desc("print this message").build());

        opt.addOption(Option.builder("v").longOpt("verbose")
                .desc("Print results for every mode and distribution").build());

        opt.addOption(Option.builder("m").longOpt("modality").hasArg()
                .desc("sample type:\n" +
                        "uni (Unimodal)\n" +
                        "multi (Multimodal)").build());

        opt.addOption(Option.builder().longOpt("division").hasArg()
                .desc("divide all values in sample by certain number").build());

        opt.addOption(Option.builder().longOpt("sensitivity").hasArg()
                .desc("Sensitivity of Lowland Multimodality Detector").build());

        opt.addOption(Option.builder().longOpt("precision").hasArg()
                .desc("Precision of Lowland MultiModality Detector").build());

        opt.addOption(Option.builder().longOpt("error").hasArg()
                .desc("Error in pvalue of criteria").build());

        opt.addOption(Option.builder("c").longOpt("criteria").hasArg()
                .desc("criteria type:\n" +
                        "cvm (Cramer Von Mises)\n" +
                        "ks (Kolmogorov-Smirnov)\n" +
                        "pearson (Pearson)\n" +
                        "multi (Multicriteria)").build());
        opt.addOption(Option.builder("e").longOpt("estimator").hasArg()
                .desc("parameter estimator type:\n" +
                        "mle (Maximum Likelihood Estimation)\n" +
                        "cvm (Cramer Von Mises)\n" +
                        "ks (Kolmogorov-Smirnov)\n" +
                        "pearson (Pearson)\n" +
                        "multi (Multicriteria)").build());

        config = new Configuration();
        try {
            CommandLine cmd = new DefaultParser().parse(opt, args);
            HelpFormatter formatter = new HelpFormatter();
            String usage = "java -jar statanalyzercli.jar [options] file...";

            if (cmd.hasOption("h")) {
                formatter.printHelp(usage, opt);
                System.exit(0);
            }

            if (cmd.hasOption("v")) {
                config.verbose = true;
            } else {
                config.verbose = DEFVERBOSITY;
            }

            try {
                config.division = Double.parseDouble(cmd.getOptionValue("division", String.valueOf(DEFDIVISION)));
            } catch (NumberFormatException e) {
                System.err.println("division must be a double number");
                formatter.printHelp(usage, opt);
                System.exit(1);
            }

            try {
                config.sensitivity = Double.parseDouble(cmd.getOptionValue("sensitivity", String.valueOf(DEFSENSITIVITY)));
                if (config.sensitivity <= 0 || config.sensitivity >= 1) {
                    System.err.println("sensitivity must be in a range from 0 to 1");
                    formatter.printHelp(usage, opt);
                    System.exit(1);
                }
            } catch (NumberFormatException e) {
                System.err.println("sensitivity must be a double number");
                formatter.printHelp(usage, opt);
                System.exit(1);
            }

            try {
                config.precision = Double.parseDouble(cmd.getOptionValue("precision", String.valueOf(DEFPRECISION)));
                if (config.precision <= 0 || config.precision >= 1) {
                    System.err.println("precision must be in a range from 0 to 1");
                    formatter.printHelp(usage, opt);
                    System.exit(1);
                }
            } catch (NumberFormatException e) {
                System.err.println("precision must be a double number");
                formatter.printHelp(usage, opt);
                System.exit(1);
            }

            try {
                config.error = Double.parseDouble(cmd.getOptionValue("error", String.valueOf(DEFERROR)));
                if (config.error <= 0 || config.error > 1) {
                    System.err.println("error must be in a range from 0 to 1");
                    formatter.printHelp(usage, opt);
                    System.exit(1);
                }
            } catch (NumberFormatException e) {
                System.err.println("error must be a double number");
                formatter.printHelp(usage, opt);
                System.exit(1);
            }

            try {
                config.modality = Configuration.Modality.valueOf(cmd.getOptionValue("m", DEFMODALITY).toUpperCase());
            } catch (IllegalArgumentException e) {
                System.out.println(String.format("Invalid modality specified \"%s\"\n", cmd.getOptionValue("m")));
                formatter.printHelp(usage, opt);
                System.exit(1);
            }

            try {
                config.criteria = Configuration.TestCriteria.valueOf(cmd.getOptionValue("c", DEFTEST).toUpperCase());
            } catch (IllegalArgumentException e) {
                System.out.println(String.format("Invalid criteria specified \"%s\"\n", cmd.getOptionValue("c")));
                formatter.printHelp(usage, opt);
                System.exit(1);

            }

            try {
                config.estimator = Configuration.ParamsEstimator.valueOf(cmd.getOptionValue("e", DEFESTIMATOR).toUpperCase());
            } catch (IllegalArgumentException e) {
                System.out.println(String.format("Invalid estimator specified \"%s\"\n", cmd.getOptionValue("e")));
                formatter.printHelp(usage, opt);
                System.exit(1);
            }

            if (cmd.getArgList().isEmpty()) {
                System.out.println("Filename is not specified\n");
                formatter.printHelp(usage, opt);
                System.exit(1);
            }
            config.filename = cmd.getArgList().get(0);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}