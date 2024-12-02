package ru.postgrespro.perf.pgmicrobench;

//import ru.postgrespro.perf.pgmicrobench.statanalyzer.distributions.PgDistribution;
import org.apache.commons.cli.*;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.Sample;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.console.Configuration;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.distributions.PgDistributionType;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.distributions.recognition.FittedDistribution;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.distributions.recognition.IDistributionTest;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.distributions.recognition.IParameterEstimator;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.multimodality.LowlandModalityDetector;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.multimodality.ModalityData;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.multimodality.RangedMode;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.plotting.Plot;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class Main {
    private static final String DEFMODALITY = "multi";
    private static final String DEFTEST = "ks";
    private static final String DEFESTIMATOR = "ks";

    private static Configuration config;
    private static final PgDistributionType[] supportedDistributions = PgDistributionType.values();
    private static final LowlandModalityDetector detector = new LowlandModalityDetector(0.5, 0.01, false);


    public static void main(String[] args) {
        parseArgs(args);

        List<Double> dataList = new ArrayList<>(10000);
        try (Scanner scanner = new Scanner(new File(config.filename))) {
            while (scanner.hasNextDouble()) {
                dataList.add(scanner.nextDouble() / 100000.0);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        switch (config.modality) {
            case UNI -> {
                evaluateMode(new Sample(dataList));
            }

            case MULTI -> {
                Sample sample = new Sample(dataList, true);
                ModalityData result = detector.detectModes(sample);

                System.out.println("Detected modality: " + result.getModality());

                for (RangedMode mode : result.getModes()) {
                    System.out.println("Processing mode: " + mode);

                    Sample modeData = new Sample(dataList.stream()
                            .filter(value -> value >= mode.getLeft() && value <= mode.getRight())
                            .collect(Collectors.toList()), false);
                    evaluateMode(modeData);
                }
            }
        }
    }

    private static void evaluateMode(Sample modeData) {
        System.out.println("Data size in this mode: " + modeData.size());

        Sample paramsSample = new Sample(
                IntStream.iterate(0, n -> n + 2).limit((modeData.size() + 1) / 2)
                        .mapToObj(modeData::get)
                        .collect(Collectors.toList()));
        Sample testSample = new Sample(
                IntStream.iterate(1, n -> n + 2).limit(modeData.size() / 2)
                        .mapToObj(modeData::get)
                        .collect(Collectors.toList()));

        IDistributionTest distCriteria = config.criteria.getCriteria();
        IParameterEstimator paramEstimator = config.estimator.getEstimator();
        for (PgDistributionType distributionType : supportedDistributions) {
            evaluateDistribution(distributionType, paramsSample, testSample, modeData, distCriteria, paramEstimator);
        }
    }

    private static void evaluateDistribution(PgDistributionType distributionType,
                                             Sample paramsSample, Sample testSample, Sample modeData,
                                             IDistributionTest distCriteria, IParameterEstimator paramEstimator) {
        System.out.println("Fitting distribution: " + distributionType.name());
        FittedDistribution fd;
        try {
            fd = paramEstimator.fit(paramsSample, distributionType);
        } catch (Exception e) {
            System.out.println("Cant find parameters: " + distributionType.name());
            System.out.println();
            return;
        }

        Plot.plot(modeData, fd.getDistribution()::pdf, distributionType.name() + " (Mode)");

        System.out.println("Params: " + Arrays.toString(fd.getParams()));

        double pValue = distCriteria.test(testSample, fd.getDistribution());
        System.out.println("pValue: " + pValue);

        System.out.println();
    }

    private static void parseArgs(String[] args) {
        Options opt = new Options();

        opt.addOption(Option.builder("h").longOpt("help")
                .desc("print this message").build());

        opt.addOption(Option.builder("m").longOpt("modality").hasArg()
                .desc("sample type: multimodal").build());

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

            config.modality = Configuration.Modality.valueOf(cmd.getOptionValue("m", DEFMODALITY).toUpperCase());
            config.criteria = Configuration.TestCriteria.valueOf(cmd.getOptionValue("c", DEFTEST).toUpperCase());
            config.estimator = Configuration.ParamsEstimator.valueOf(cmd.getOptionValue("e", DEFESTIMATOR).toUpperCase());

            if (cmd.getArgList().isEmpty()) {
                System.err.println("Filename is not specified");
                throw new IllegalArgumentException();
            }
            config.filename = cmd.getArgList().get(0);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}