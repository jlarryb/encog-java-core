package org.encog.ml.ea.opp.holder;

import java.io.Serializable;

import org.encog.ml.ea.opp.EvolutionaryOperator;

public class OperationHolder implements Serializable {
	private final EvolutionaryOperator opp;
	private final double probability;
	
	public OperationHolder(EvolutionaryOperator opp, double probability) {
		super();
		this.opp = opp;
		this.probability = probability;
	}

	/**
	 * @return the opp
	 */
	public EvolutionaryOperator getOpp() {
		return opp;
	}

	/**
	 * @return the probability
	 */
	public double getProbability() {
		return probability;
	}
	
	
	
}
