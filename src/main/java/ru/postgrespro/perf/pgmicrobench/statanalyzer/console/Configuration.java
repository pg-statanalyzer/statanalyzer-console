package ru.postgrespro.perf.pgmicrobench.statanalyzer.console;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ru.postgrespro.perf.pgmicrobench.statanalyzer.distributions.recognition.*;

public class Configuration {
    public Modality modality;
    public TestCriteria criteria;
    public ParamsEstimator estimator;
    public String filename;

    public double division;
    public double sensitivity;
    public double precision;

    public boolean verbose;


    public enum Modality {
        UNI,
        MULTI
    }

    @RequiredArgsConstructor
    public enum TestCriteria {
        CVM(new CramerVonMises()),
        KS(new KolmogorovSmirnov()),
        PEARSON(new Pearson()),
        MULTI(null);

        @Getter
        private final IDistributionTest criteria;
    }

    @RequiredArgsConstructor
    public enum ParamsEstimator {
        CVM(new CramerVonMises()),
        KS(new KolmogorovSmirnov()),
        PEARSON(new Pearson()),
        MLE(new MaximumLikelihoodEstimation()),
        MULTI(null);

        @Getter
        private final IParameterEstimator estimator;
    }
}
