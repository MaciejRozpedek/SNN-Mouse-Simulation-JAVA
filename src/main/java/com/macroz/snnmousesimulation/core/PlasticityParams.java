package com.macroz.snnmousesimulation.core;

import com.macroz.snnmousesimulation.utility.ParamChecks;

/**
 * Parameters of dopamine-modulated STDP.
 */
public record PlasticityParams(Stdp stdp, Dopamine dopamine) {

    public record Stdp(
            double tauPlusMs,
            double tauMinusMs,
            double aPlus,
            double aMinus,
            double tauEligibilityMs,
            double maxWeight
    ) {
        public Stdp {
            ParamChecks.positive("tau_plus_ms", tauPlusMs);
            ParamChecks.positive("tau_minus_ms", tauMinusMs);
            ParamChecks.nonNegative("a_plus", aPlus);
            ParamChecks.nonNegative("a_minus", aMinus);
            ParamChecks.positive("tau_eligibility_ms", tauEligibilityMs);
            ParamChecks.positive("max_weight", maxWeight);
        }
    }

    public record Dopamine(double tauMs, double tauBaselineMs, double initialBaseline) {
        public Dopamine {
            ParamChecks.positive("tau_ms", tauMs);
            ParamChecks.positive("tau_baseline_ms", tauBaselineMs);
            ParamChecks.finite("initial_baseline", initialBaseline);
        }
    }
}
