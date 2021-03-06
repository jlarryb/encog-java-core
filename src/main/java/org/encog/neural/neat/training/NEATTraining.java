/*
 * Encog(tm) Core v3.2 - Java Version
 * http://www.heatonresearch.com/encog/
 * http://code.google.com/p/encog-java/
 
 * Copyright 2008-2012 Heaton Research, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *   
 * For more information on Heaton Research copyrights, licenses 
 * and trademarks visit:
 * http://www.heatonresearch.com/copyright
 */
package org.encog.neural.neat.training;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.encog.mathutil.randomize.RandomChoice;
import org.encog.mathutil.randomize.RangeRandomizer;
import org.encog.ml.MLContext;
import org.encog.ml.MLMethod;
import org.encog.ml.TrainingImplementationType;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.ea.genome.Genome;
import org.encog.ml.ea.opp.selection.PrgSelection;
import org.encog.ml.ea.population.Population;
import org.encog.ml.ea.score.CalculateGenomeScore;
import org.encog.ml.ea.sort.GenomeComparator;
import org.encog.ml.ea.sort.MinimizeAdjustedScoreComp;
import org.encog.ml.ea.sort.MinimizeScoreComp;
import org.encog.ml.ea.train.EvolutionaryAlgorithm;
import org.encog.ml.prg.train.GeneticTrainingParams;
import org.encog.ml.train.MLTrain;
import org.encog.ml.train.strategy.Strategy;
import org.encog.neural.neat.NEATNetwork;
import org.encog.neural.neat.NEATPopulation;
import org.encog.neural.neat.NEATSpecies;
import org.encog.neural.networks.training.CalculateScore;
import org.encog.neural.networks.training.TrainingError;
import org.encog.neural.networks.training.genetic.GeneticScoreAdapter;
import org.encog.neural.networks.training.propagation.TrainingContinuation;

/**
 * The two parents.
 */
enum NEATParent {
	/**
	 * The father.
	 */
	Dad,

	/**
	 * The mother.
	 */
	Mom
};

/**
 * Implements NEAT genetic training.
 * 
 * NeuroEvolution of Augmenting Topologies (NEAT) is a genetic algorithm for the
 * generation of evolving artificial neural networks. It was developed by Ken
 * Stanley while at The University of Texas at Austin.
 * 
 * http://www.cs.ucf.edu/~kstanley/
 * 
 */
public class NEATTraining implements MLTrain, EvolutionaryAlgorithm {

	/**
	 * The average fit adjustment.
	 */
	private double averageFitAdjustment;

	/**
	 * The best ever score.
	 */
	private double bestEverScore;

	/**
	 * The best ever network.
	 */
	private NEATNetwork bestEverNetwork;

	/**
	 * The number of inputs.
	 */
	private final int inputCount;

	/**
	 * The number of output neurons.
	 */
	private final int outputCount;

	/**
	 * The total fit adjustment.
	 */
	private double totalFitAdjustment;

	/**
	 * Determines if we are using snapshot mode.
	 */
	private boolean snapshot;

	/**
	 * The iteration number.
	 */
	private int iteration;
	
	/**
	 * The parameters of NEAT.
	 */
	private final NEATParams params = new NEATParams();
	
	private RandomChoice mutateChoices;
	
	private RandomChoice mutateAddChoices;
	
	/**
	 * The score calculation object.
	 */
	private CalculateGenomeScore calculateScore;

	/**
	 * The genome comparator.
	 */
	private GenomeComparator selectionComparator;
	
	private GenomeComparator bestComparator;
	
	private NEATPopulation population;

	/**
	 * Construct a neat trainer with a new population. The new population is
	 * created from the specified parameters.
	 * 
	 * @param calculateScore
	 *            The score calculation object.
	 * @param inputCount
	 *            The input neuron count.
	 * @param outputCount
	 *            The output neuron count.
	 * @param populationSize
	 *            The population size.
	 */
	public NEATTraining(final CalculateScore calculateScore,
			final int inputCount, final int outputCount,
			final int populationSize) {

		this.inputCount = inputCount;
		this.outputCount = outputCount;

		setCalculateScore(new GeneticScoreAdapter(calculateScore));
		setBestComparator(new MinimizeScoreComp());
		setSelectionComparator(new MinimizeAdjustedScoreComp());
		setPopulation(new NEATPopulation(inputCount, outputCount,
				populationSize));

		init();
	}

	/**
	 * Construct neat training with an existing population.
	 * 
	 * @param calculateScore
	 *            The score object to use.
	 * @param population
	 *            The population to use.
	 */
	public NEATTraining(final CalculateScore calculateScore,
			final NEATPopulation population) {
		if (population.size() < 1) {
			throw new TrainingError("Population can not be empty.");
		}

		final NEATGenome genome = (NEATGenome) population.getGenomes().get(0);
		setCalculateScore(new GeneticScoreAdapter(calculateScore));
		setBestComparator(new MinimizeScoreComp());
		setSelectionComparator(new MinimizeAdjustedScoreComp());
		setPopulation(population);
		this.inputCount = genome.getInputCount();
		this.outputCount = genome.getOutputCount();
		init();
	}

	/**
	 * Add a neuron.
	 * 
	 * @param nodeID
	 *            The neuron id.
	 * @param vec
	 *            THe list of id's used.
	 */
	public void addNeuronID(final long nodeID, final List<Long> vec) {
		for (int i = 0; i < vec.size(); i++) {
			if (vec.get(i) == nodeID) {
				return;
			}
		}

		vec.add(nodeID);

		return;
	}

	/**
	 * Not supported, will throw an error.
	 * 
	 * @param strategy
	 *            Not used.
	 */
	@Override
	public void addStrategy(final Strategy strategy) {
		throw new TrainingError(
				"Strategies are not supported by this training method.");
	}

	/**
	 * Adjust the species compatibility threshold. This prevents us from having
	 * too many species.
	 */
	public void adjustCompatibilityThreshold() {

		// has this been disabled (unlimited species)
		if (this.params.maxNumberOfSpecies < 1) {
			return;
		}

		final double thresholdIncrement = 0.01;

		if (getPopulation().getSpecies().size() > this.params.maxNumberOfSpecies) {
			this.params.compatibilityThreshold += thresholdIncrement;
		}

		else if (getPopulation().getSpecies().size() < 2) {
			this.params.compatibilityThreshold -= thresholdIncrement;
		}

	}

	/**
	 * Adjust each species score.
	 */
	public void adjustSpeciesScore() {
		for (final NEATSpecies s : getPopulation().getSpecies()) {
			// loop over all genomes and adjust scores as needed
			for (final Genome member : s.getMembers()) {
				double score = member.getScore();

				// apply a youth bonus
				if (s.getAge() < getPopulation().getYoungBonusAgeThreshold()) {
					score = getSelectionComparator().applyBonus(score,
							getPopulation().getYoungScoreBonus());
				}

				// apply an old age penalty
				if (s.getAge() > getPopulation().getOldAgeThreshold()) {
					score = getSelectionComparator().applyPenalty(score,
							getPopulation().getOldAgePenalty());
				}

				final double adjustedScore = score / s.getMembers().size();

				member.setAdjustedScore(adjustedScore);

			}
		}
	}

	@Override
	public boolean canContinue() {
		return false;
	}
	
	/**
	 * Choose a parent to favor.
	 * @param mom The mother.
	 * @param dad The father.
	 * @return The parent to favor.
	 */
	private NEATParent favorParent(final NEATGenome mom, final NEATGenome dad) {
		
		// first determine who is more fit, the mother or the father?
		// see if mom and dad are the same fitness
		if (mom.getScore() == dad.getScore()) {
			// are mom and dad the same fitness
			if (mom.getNumGenes() == dad.getNumGenes()) {
				// if mom and dad are the same fitness and have the same number of genes, 
				// then randomly pick mom or dad as the most fit.
				if (Math.random() > 0) {
					return NEATParent.Mom;
				} else {
					return NEATParent.Dad;
				}
			}
			// mom and dad are the same fitness, but different number of genes
			// favor the parent with fewer genes
			else {
				if (mom.getNumGenes() < dad.getNumGenes()) {
					return NEATParent.Mom;
				} else {
					return NEATParent.Dad;
				}
			}
		} else {
			// mom and dad have different scores, so choose the better score.
			// important to note, better score COULD BE the larger or smaller score.
			if (getSelectionComparator().compare(mom, dad)<0) {
				return NEATParent.Mom;
			}

			else {
				return NEATParent.Dad;
			}
		}
		
	}

	/**
	 * Perform the crossover.
	 * 
	 * @param mom
	 *            The mother.
	 * @param dad
	 *            The father.
	 * @return The child.
	 */
	public NEATGenome crossover(final NEATGenome mom, final NEATGenome dad) {
		NEATParent best = favorParent(mom,dad);

		final List<NEATNeuronGene> babyNeurons = new ArrayList<NEATNeuronGene>();
		final List<NEATLinkGene> babyGenes = new ArrayList<NEATLinkGene>();

		final List<Long> vecNeurons = new ArrayList<Long>();

		int curMom = 0; // current gene index from mom
		int curDad = 0; // current gene index from dad
		NEATLinkGene selectedGene = null;
		
		// add in the input and bias, they should always be here
		int alwaysCount = this.inputCount + this.outputCount + 1;
		for(int i=0;i<alwaysCount;i++) {
			addNeuronID(i, vecNeurons);
		}
		

		while ((curMom < mom.getNumGenes()) || (curDad < dad.getNumGenes())) {
			NEATLinkGene momGene = null; // the mom gene object
			NEATLinkGene dadGene = null; // the dad gene object
			

			// grab the actual objects from mom and dad for the specified indexes
			// if there are none, then null
			if (curMom < mom.getNumGenes()) {
				momGene = (NEATLinkGene) mom.getLinksChromosome().get(curMom);
			} 

			if (curDad < dad.getNumGenes()) {
				dadGene = (NEATLinkGene) dad.getLinksChromosome().get(curDad);
			} 

			// now select a gene for mom or dad.  This gene is for the baby
			if ((momGene == null) && (dadGene != null)) {
				if (best == NEATParent.Dad) {
					selectedGene = dadGene;
				}
				curDad++;
			} else if ((dadGene == null) && (momGene != null)) {
				if (best == NEATParent.Mom) {
					selectedGene = momGene;
				}
				curMom++;
			} else if (momGene.getInnovationId() < dadGene.getInnovationId()) {
				if (best == NEATParent.Mom) {
					selectedGene = momGene;
				}
				curMom++;
			} else if (dadGene.getInnovationId() < momGene.getInnovationId()) {
				if (best == NEATParent.Dad) {
					selectedGene = dadGene;
				}
				curDad++;
			} else if (dadGene.getInnovationId() == momGene.getInnovationId()) {
				if (Math.random() < 0.5f) {
					selectedGene = momGene;
				}

				else {
					selectedGene = dadGene;
				}
				curMom++;
				curDad++;
			}

			if( selectedGene!=null ) {
				if (babyGenes.size() == 0) {
					babyGenes.add(selectedGene);
				} else {
					if (((NEATLinkGene) babyGenes.get(babyGenes.size() - 1))
							.getInnovationId() != selectedGene.getInnovationId()) {
						babyGenes.add(selectedGene);
					}
				}
	
				// Check if we already have the nodes referred to in SelectedGene.
				// If not, they need to be added.
				addNeuronID(selectedGene.getFromNeuronID(), vecNeurons);
				addNeuronID(selectedGene.getToNeuronID(), vecNeurons);
			}

		}// end while

		// now create the required nodes. First sort them into order
		Collections.sort(vecNeurons);

		for (int i = 0; i < vecNeurons.size(); i++) {
			babyNeurons.add(getInnovations().createNeuronFromID(
					vecNeurons.get(i)));
		}

		// finally, create the genome
		final NEATGenome babyGenome = new NEATGenome(getPopulation()
				.assignGenomeID(), babyNeurons, babyGenes, mom.getInputCount(),
				mom.getOutputCount());
		babyGenome.setPopulation(getPopulation());
		
		return babyGenome;
	}

	/**
	 * Called when training is done.
	 */
	@Override
	public void finishTraining() {

	}

	/**
	 * return The error for the best genome.
	 */
	@Override
	public double getError() {
		return this.bestEverScore;
	}

	@Override
	public TrainingImplementationType getImplementationType() {
		return TrainingImplementationType.Iterative;
	}

	/**
	 * @return The innovations.
	 */
	public NEATInnovationList getInnovations() {
		return (NEATInnovationList) getPopulation().getInnovations();
	}

	/**
	 * @return The input count.
	 */
	public int getInputCount() {
		return this.inputCount;
	}

	@Override
	public int getIteration() {
		return this.iteration;
	}

	/**
	 * @return A network created for the best genome.
	 */
	@Override
	public MLMethod getMethod() {
		return this.bestEverNetwork;
	}

	/**
	 * @return The number of output neurons.
	 */
	public int getOutputCount() {
		return this.outputCount;
	}

	/**
	 * Returns an empty list, strategies are not supported.
	 * 
	 * @return The strategies in use(none).
	 */
	@Override
	public List<Strategy> getStrategies() {
		return new ArrayList<Strategy>();
	}

	/**
	 * Returns null, does not use a training set, rather uses a score function.
	 * 
	 * @return null, not used.
	 */
	@Override
	public MLDataSet getTraining() {
		return null;
	}

	/**
	 * setup for training.
	 */
	private void init() {
		this.mutateChoices = new RandomChoice(new double[] {0.988, 0.001, 0.01, 0.0, 0.001 } );
		this.mutateAddChoices = new RandomChoice(new double[] {0.988, 0.001, 0.01, 0.0 } );
		
		if (getCalculateScore().shouldMinimize()) {
			this.bestEverScore = Double.MAX_VALUE;
		} else {
			this.bestEverScore = Double.MIN_VALUE;
		}

		// check the population
		for (final Genome obj : getPopulation().getGenomes()) {
			if (!(obj instanceof NEATGenome)) {
				throw new TrainingError(
						"Population can only contain objects of NEATGenome.");
			}

			final NEATGenome neat = (NEATGenome) obj;

			if ((neat.getInputCount() != this.inputCount)
					|| (neat.getOutputCount() != this.outputCount)) {
				throw new TrainingError(
						"All NEATGenome's must have the same input and output sizes as the base network.");
			}
		}

		resetAndKill();
		sortAndRecord();
		speciateAndCalculateSpawnLevels();
	}

	/**
	 * @return Are we using snapshot mode.
	 */
	public boolean isSnapshot() {
		return this.snapshot;
	}

	/**
	 * @return True if training can progress no further.
	 */
	@Override
	public boolean isTrainingDone() {
		return false;
	}

	/**
	 * Perform one training iteration.
	 */
	@Override
	public void iteration() {

		this.iteration++;
		final List<NEATGenome> newPop = new ArrayList<NEATGenome>();

		int numSpawnedSoFar = 0;

		for (final NEATSpecies s : getPopulation().getSpecies()) {
			if (numSpawnedSoFar < getPopulation().size()) {
				int numToSpawn = (int) Math.round(s.getNumToSpawn());

				boolean bChosenBestYet = false;

				while ((numToSpawn--) > 0) {
					NEATGenome baby = null;

					if (!bChosenBestYet) {
						baby = (NEATGenome) s.getLeader();

						bChosenBestYet = true;
					}

					else {
						// if the number of individuals in this species is only
						// one
						// then we can only perform mutation
						if (s.getMembers().size() == 1) {
							// spawn a child
							baby = new NEATGenome((NEATGenome) s.chooseParent());
						} else {
							final NEATGenome g1 = (NEATGenome) s.chooseParent();

							if (Math.random() < this.params.crossoverRate) {
								NEATGenome g2 = (NEATGenome) s.chooseParent();

								int numAttempts = 5;

								while ((g1.getGenomeID() == g2.getGenomeID())
										&& ((numAttempts--) > 0)) {
									g2 = (NEATGenome) s.chooseParent();
								}

								if (g1.getGenomeID() != g2.getGenomeID()) {
									baby = crossover(g1, g2);
								}
							}

							else {
								baby = new NEATGenome(g1);
							}
						}

						if (baby != null) {
							baby.setGenomeID(getPopulation().assignGenomeID());
							mutate(baby);
						}
					}

					if (baby != null) {
						// sort the baby's genes by their innovation numbers
						baby.sortGenes();

						// add to new pop
						// if (newPop.contains(baby)) {
						// throw new EncogErrorthis.params("readd");
						// }
						newPop.add(baby);

						++numSpawnedSoFar;

						if (numSpawnedSoFar == getPopulation().size()) {
							numToSpawn = 0;
						}
					}
				}
			}
		}

		while (newPop.size() < getPopulation().size()) {
			newPop.add(tournamentSelection(getPopulation().size() / 5));
		}

		getPopulation().clear();
		getPopulation().addAll(newPop);

		resetAndKill();
		sortAndRecord();
		speciateAndCalculateSpawnLevels();
	}
	
	public void mutate(NEATGenome genome) {
		int option = this.mutateChoices.generate(new Random());
		
		switch(option) {
			case 0: // mutate weight
				genome.mutateWeights(this.params.mutationRate,
						this.params.probabilityWeightReplaced,
						this.params.maxWeightPerturbation);
				break;
			case 1: // add node
				if (genome.getNeuronsChromosome().size() < this.params.maxPermittedNeurons) {
					genome.addNeuron(this, this.params.chanceAddNode,
							this.params.numTrysToFindOldLink);
				}
				break;
			case 2: // add connection
				// now there's the chance a link may be added
				genome.addLink(	this, this.params.numTrysToFindLoopedLink,
						this.params.numAddLinkAttempts);
				break;
			case 3: // adjust curve
				break;
			case 4: // remove connection
				genome.removeLink();
				break;
		}
	}

	/**
	 * Perform the specified number of training iterations. This is a basic
	 * implementation that just calls iteration the specified number of times.
	 * However, some training methods, particularly with the GPU, benefit
	 * greatly by calling with higher numbers than 1.
	 * 
	 * @param count
	 *            The number of training iterations.
	 */
	@Override
	public void iteration(final int count) {
		for (int i = 0; i < count; i++) {
			iteration();
		}
	}

	@Override
	public TrainingContinuation pause() {
		return null;
	}

	/**
	 * Reset for an iteration.
	 */
	public void resetAndKill() {
		this.totalFitAdjustment = 0;
		this.averageFitAdjustment = 0;

		final Object[] speciesArray = getPopulation().getSpecies().toArray();

		for (final Object element : speciesArray) {
			final NEATSpecies s = (NEATSpecies) element;
			s.purge();

			// did the leader die?  If so, disband the species.
			if( !getPopulation().getGenomes().contains(s.getLeader())) {
				getPopulation().getSpecies().remove(s);
			}
			else if ((s.getGensNoImprovement() > this.params.numGensAllowedNoImprovement)
					&& getSelectionComparator().isBetterThan(this.bestEverScore,
							s.getBestScore())) {
				getPopulation().getSpecies().remove(s);
			}
		}
	}

	@Override
	public void resume(final TrainingContinuation state) {

	}

	/**
	 * Not used.
	 * 
	 * @param error
	 *            Not used.
	 */
	@Override
	public void setError(final double error) {
	}

	@Override
	public void setIteration(final int iteration) {
		this.iteration = iteration;
	}


	/**
	 * Set if we are using snapshot mode.
	 * 
	 * @param snapshot
	 *            True if we are using snapshot mode.
	 */
	public void setSnapshot(final boolean snapshot) {
		this.snapshot = snapshot;
	}

	/**
	 * Sort the genomes.
	 */
	public void sortAndRecord() {

		for (final Genome genome : getPopulation().getGenomes()) {
			genome.decode();
			calculateScore(genome);
		}

		getPopulation().sort(this.bestComparator);

		final Genome genome = getPopulation().get(0);
		final double currentBest = genome.getScore();

		if (getSelectionComparator().isBetterThan(currentBest, this.bestEverScore)) {
			this.bestEverScore = currentBest;
			this.bestEverNetwork = ((NEATNetwork) genome.getOrganism());
		}

		if( getSelectionComparator().isBetterThan(getError(), this.bestEverScore) ) {
			this.bestEverScore = getError();
		}
		
	}

	/**
	 * Determine the species.
	 */
	public void speciateAndCalculateSpawnLevels() {

		// calculate compatibility between genomes and species
		adjustCompatibilityThreshold();

		// assign genomes to species (if any exist)
		for (final Genome g : getPopulation().getGenomes()) {
			final NEATGenome genome = (NEATGenome) g;
			boolean added = false;

			for (final NEATSpecies s : getPopulation().getSpecies()) {
				final double compatibility = genome
						.getCompatibilityScore((NEATGenome) s.getLeader());

				if (compatibility <= this.params.compatibilityThreshold) {
					addSpeciesMember(s, genome);
					genome.setSpeciesID(s.getSpeciesID());
					added = true;
					break;
				}
			}

			// if this genome did not fall into any existing species, create a
			// new species
			if (!added) {
				getPopulation().getSpecies().add(
						new NEATSpecies(getPopulation(), genome,
								getPopulation().assignSpeciesID()));
			}
		}

		adjustSpeciesScore();

		for (final Genome g : getPopulation().getGenomes()) {
			final NEATGenome genome = (NEATGenome) g;
			this.totalFitAdjustment += genome.getAdjustedScore();
		}

		this.averageFitAdjustment = this.totalFitAdjustment
				/ getPopulation().size();

		for (final Genome g : getPopulation().getGenomes()) {
			final NEATGenome genome = (NEATGenome) g;
			final double toSpawn = genome.getAdjustedScore()
					/ this.averageFitAdjustment;
			genome.setAmountToSpawn(toSpawn);
		}

		for (final NEATSpecies species : getPopulation().getSpecies()) {
			species.calculateSpawnAmount();
		}
	}

	/**
	 * Select a gene using a tournament.
	 * 
	 * @param numComparisons
	 *            The number of compares to do.
	 * @return The chosen genome.
	 */
	public NEATGenome tournamentSelection(final int numComparisons) {
		double bestScoreSoFar = 0;

		int ChosenOne = 0;

		for (int i = 0; i < numComparisons; ++i) {
			final int ThisTry = (int) RangeRandomizer.randomize(0,
					getPopulation().size() - 1);

			if (getPopulation().get(ThisTry).getScore() > bestScoreSoFar) {
				ChosenOne = ThisTry;

				bestScoreSoFar = getPopulation().get(ThisTry).getScore();
			}
		}

		return (NEATGenome) getPopulation().get(ChosenOne);
	}
	
	/**
	 * Add a genome.
	 * 
	 * @param species
	 *            The species to add.
	 * @param genome
	 *            The genome to add.
	 */
	public void addSpeciesMember(final NEATSpecies species, 
			final NEATGenome genome) {

		if (getSelectionComparator().compare(genome,species.getLeader())<0) {
			species.setBestScore(genome.getScore());
			species.setGensNoImprovement(0);
			species.setLeader(genome);
		}

		species.getMembers().add(genome);

	}

	/**
	 * @return the calculateScore
	 */
	public CalculateGenomeScore getCalculateScore() {
		return calculateScore;
	}

	/**
	 * @param calculateScore the calculateScore to set
	 */
	public void setCalculateScore(CalculateGenomeScore calculateScore) {
		this.calculateScore = calculateScore;
	}

	/**
	 * @return the population
	 */
	public NEATPopulation getPopulation() {
		return population;
	}

	/**
	 * @param population the population to set
	 */
	public void setPopulation(NEATPopulation population) {
		this.population = population;
	}
	
	/**
	 * Calculate the score for this genome. The genome's score will be set.
	 * 
	 * @param g
	 *            The genome to calculate for.
	 */
	public void calculateScore(final Genome g) {
		if (g.getOrganism() instanceof MLContext) {
			((MLContext) g.getOrganism()).clearContext();
		}
		final double score = this.calculateScore.calculateScore(g);
		g.setScore(score);
	}

	@Override
	public void setPopulation(Population thePopulation) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void calculateEffectiveScore(Genome genome) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public GeneticTrainingParams getParams() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getMaxIndividualSize() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public PrgSelection getSelection() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setSelection(PrgSelection selection) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @return the selectionComparator
	 */
	@Override
	public GenomeComparator getSelectionComparator() {
		return selectionComparator;
	}

	/**
	 * @param selectionComparator the selectionComparator to set
	 */
	@Override
	public void setSelectionComparator(GenomeComparator selectionComparator) {
		this.selectionComparator = selectionComparator;
	}

	/**
	 * @return the bestComparator
	 */
	@Override
	public GenomeComparator getBestComparator() {
		return bestComparator;
	}

	/**
	 * @param bestComparator the bestComparator to set
	 */
	@Override
	public void setBestComparator(GenomeComparator bestComparator) {
		this.bestComparator = bestComparator;
	}
	
	

}
