/*
 * Created on 11-Oct-2006
 *
 * Copyright  2006 HBA Specto Incorporated
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.hbaspecto.pecas.sd;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrices;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.Vector;

import org.apache.log4j.Logger;

import simpleorm.dataset.SQuery;

import com.hbaspecto.discreteChoiceModelling.Coefficient;
import com.hbaspecto.pecas.land.PostgreSQLLandInventory;
import com.hbaspecto.pecas.land.SimpleORMLandInventory;
import com.hbaspecto.pecas.sd.estimation.DifferentiableModel;
import com.hbaspecto.pecas.sd.estimation.EstimationDataSet;
import com.hbaspecto.pecas.sd.estimation.EstimationReader;
import com.hbaspecto.pecas.sd.estimation.EstimationTarget;
import com.hbaspecto.pecas.sd.estimation.ExpectedTargetModel;
import com.hbaspecto.pecas.sd.estimation.GaussBayesianObjective;
import com.hbaspecto.pecas.sd.estimation.MarquardtMinimizer;
import com.hbaspecto.pecas.sd.estimation.OptimizationException;
import com.hbaspecto.pecas.sd.orm.ExchangeResults_gen;
import com.hbaspecto.pecas.sd.orm.ObservedDevelopmentEvents;
import com.hbaspecto.pecas.sd.orm.ObservedDevelopmentEvents_gen;
import com.hbaspecto.pecas.sd.orm.Parcels_gen;
import com.hbaspecto.pecas.sd.orm.SiteSpecTotals_gen;
import com.hbaspecto.pecas.sd.orm.ZoningPermissions_gen;
import com.pb.common.datafile.CSVFileWriter;
import com.pb.common.datafile.GeneralDecimalFormat;
import com.pb.common.datafile.JDBCTableReader;
import com.pb.common.datafile.JDBCTableWriter;
import com.pb.common.datafile.OLD_CSVFileReader;
import com.pb.common.datafile.TableDataFileReader;
import com.pb.common.datafile.TableDataFileWriter;
import com.pb.common.datafile.TableDataReader;
import com.pb.common.datafile.TableDataSetCollection;
import com.pb.common.datafile.TableDataWriter;
import com.pb.common.sql.JDBCConnection;
import com.pb.common.util.ResourceUtil;

public class StandardSDModel extends SDModel {

	static boolean excelLandDatabase;

	static boolean useSQLParcels = false;

	protected static transient Logger logger = Logger
			.getLogger(StandardSDModel.class);

	protected String landDatabaseUser;

	protected String landDatabasePassword, databaseSchema;

	private TableDataFileWriter writer;

	public static void main(String[] args) {
		boolean worked = true; // assume this is going to work
		rbSD = ResourceUtil.getResourceBundle("sd");
		initOrm();
		final SDModel mySD = new StandardSDModel();
		try {
			currentYear = Integer.valueOf(args[0]) + Integer.valueOf(args[1]);
			baseYear = Integer.valueOf(args[0]);
		}
		catch (final Exception e) {
			e.printStackTrace();
			throw new RuntimeException(
					"Put base year and time interval on command line"
							+ "\n For example, 1990 1");
		}
		try {
			mySD.setUp();

			// for testing, remove this line
			((SimpleORMLandInventory) mySD.land)
					.readInventoryTable("floorspacei_view");

			mySD.runSD(currentYear, baseYear, rbSD);
		}
		catch (Throwable e) {
			logger.fatal("Unexpected error " + e);
			e.printStackTrace();
			do {
				logger.fatal(e.getMessage());
				final StackTraceElement elements[] = e.getStackTrace();
				for (int i = 0; i < elements.length; i++) {
					logger.fatal(elements[i].toString());
				}
				logger.fatal("Caused by...");
			} while ((e = e.getCause()) != null);
			worked = false; // oops it didn't work
		}
		finally {
			if (mySD.land != null) {
				mySD.land.disconnect();
			}
			if (!worked) {
				System.exit(1); // don't need to manually call exit if
				// everything worked ok.
			}
		}
	}

	public StandardSDModel() {
		super();
	}

	static void initOrm(ResourceBundle rb) {
		rbSD = rb;
		initOrm();
	}

	static void initOrm() {
		Parcels_gen.init(rbSD);
		ZoningPermissions_gen.init(rbSD);
		ExchangeResults_gen.init(rbSD);
		SiteSpecTotals_gen.init(rbSD);
	}

	@Override
	public void setUpLandInventory(String className, int year) {

		try {
			final Class landInventoryClass = Class.forName(className);
			final SimpleORMLandInventory sormland = (SimpleORMLandInventory) landInventoryClass
					.newInstance();
			land = sormland;
			sormland.setDatabaseConnectionParameter(rbSD, landDatabaseDriver,
					landDatabaseSpecifier, landDatabaseUser, landDatabasePassword,
					databaseSchema);

			sormland.setLogFile(logFilePath + "developmentEvents.csv");
			logger.info("Log file is at " + logFilePath + "developmentEvents.csv");

			land.init(year);
			land.setMaxParcelSize(ResourceUtil.getDoubleProperty(rbSD,
					"MaxParcelSize", Double.POSITIVE_INFINITY));

		}
		catch (final InstantiationException ie) {
			logger.fatal("Instantiating : " + className + '\n' + ie.getMessage());
			throw new RuntimeException("Instantiating " + className, ie);
		}
		catch (final Exception e) {
			logger
					.fatal("Can't open land database table using " + landDatabaseDriver);
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	@Override
	public void setUp() {
		useSQLInputs = ResourceUtil.getBooleanProperty(rbSD, "UseSQLInputs");
		useSQLParcels = ResourceUtil.getBooleanProperty(rbSD, "UseSQLParcels");
		TableDataReader inputTableReader;
		TableDataWriter inputTableWriter;
		inputDatabaseDriver = ResourceUtil.checkAndGetProperty(rbSD,
				"InputJDBCDriver");
		inputDatabaseSpecifier = ResourceUtil.checkAndGetProperty(rbSD,
				"InputDatabase");
		final JDBCConnection inputPBConnection = new JDBCConnection(
				inputDatabaseSpecifier, inputDatabaseDriver, ResourceUtil.getProperty(
						rbSD, "InputDatabaseUser", ""), ResourceUtil.getProperty(rbSD,
						"InputDatabasePassword", ""));
		final JDBCTableReader jdbcInputTableReader = new JDBCTableReader(
				inputPBConnection);
		final JDBCTableWriter jdbcInputTableWriter = new JDBCTableWriter(
				inputPBConnection);
		excelInputDatabase = ResourceUtil.getBooleanProperty(rbSD,
				"ExcelInputDatabase", false);
		jdbcInputTableReader.setMangleTableNamesForExcel(excelInputDatabase);
		jdbcInputTableWriter.setMangleTableNamesForExcel(excelInputDatabase);
		inputTableReader = jdbcInputTableReader;
		inputTableWriter = jdbcInputTableWriter;
		landDatabaseDriver = ResourceUtil.checkAndGetProperty(rbSD,
				"LandJDBCDriver");
		try {
			Class.forName(landDatabaseDriver).newInstance();
		}
		catch (final Exception e) {
			logger.fatal("Can't start land database driver" + e);
			throw new RuntimeException("Can't start land database driver", e);
		}
		landDatabaseSpecifier = ResourceUtil.checkAndGetProperty(rbSD,
				"LandDatabase");

		// landDatabaseTable = ResourceUtil.checkAndGetProperty(rbSD,
		// "LandTable");

		landDatabaseUser = ResourceUtil.getProperty(rbSD, "LandDatabaseUser", "");
		landDatabasePassword = ResourceUtil.getProperty(rbSD,
				"LandDatabasePassword", "");
		databaseSchema = ResourceUtil.getProperty(rbSD, "schema");

		final TableDataSetCollection inputDatabase = new TableDataSetCollection(
				inputTableReader, inputTableWriter);
		// TableDataSet developmentTypesI =
		// inputDatabase.getTableDataSet("spacetypesi");
		// TableDataSet transitionConstantsI = inputDatabase
		// .getTableDataSet("TransitionConstantsI");

		// FIXME read in the LUZ table or make sure it can be read in
		// record-by-record on demand using SimpleORM
		// PECASZone.setUpZones(inputDatabase.getTableDataSet("PECASZonesI"));

		// ZoningRulesI.setUpZoningSchemes(inputDatabase
		// .getTableDataSet("ZoningSchemesI"));

		// We'll iterate through PECASZones, instead of FloorspaceZones.
		// zoneNumbers = inputDatabase.getTableDataSet("PECASZonesI");

		logFilePath = ResourceUtil.checkAndGetProperty(rbSD, "LogFilePath");
		final String className = ResourceUtil.getProperty(rbSD,
				"LandInventoryClass", PostgreSQLLandInventory.class.getName());
		setUpLandInventory(className, currentYear);
		ZoningRulesI.land = land;
		setUpDevelopmentTypes();
		final TableDataFileReader reader = setUpCsvReaderWriter();

		// if (ResourceUtil.getBooleanProperty(rbSD,
		// "sd.aaUsesDifferentZones",false))
		// readFloorspaceZones(inputDatabase
		// .getTableDataSet("FloorspaceZonesI"));

		// need to get prices from file if it exists
		if (ResourceUtil.getBooleanProperty(rbSD, "ReadExchangeResults", true)) {
			land.readSpacePrices(reader);
		}
		if (ResourceUtil.getBooleanProperty(rbSD, "SmoothPrices", false)) {
			land.applyPriceSmoothing(reader, writer);
		}
	}

	private TableDataFileReader setUpCsvReaderWriter() {
		final OLD_CSVFileReader outputTableReader = new OLD_CSVFileReader();
		final CSVFileWriter outputTableWriter = new CSVFileWriter();
		outputTableWriter.setMyDecimalFormat(new GeneralDecimalFormat(
				"0.#########E0", 10000000, .001));
		if (ResourceUtil.getBooleanProperty(rbSD, "UseYearSubdirectories", true)) {
			outputTableReader.setMyDirectory(ResourceUtil.getProperty(rbSD,
					"AAResultsDirectory") + currentYear + File.separatorChar);
			outputTableWriter.setMyDirectory(new File(ResourceUtil.getProperty(rbSD,
					"AAResultsDirectory") + (currentYear + 1) + File.separatorChar));
		}
		else {
			outputTableReader.setMyDirectory(ResourceUtil.getProperty(rbSD,
					"AAResultsDirectory"));
			outputTableWriter.setMyDirectory(new File(ResourceUtil.getProperty(rbSD,
					"AAResultsDirectory")));
		}

		outputDatabase = new TableDataSetCollection(outputTableReader,
				outputTableWriter);
		writer = outputTableWriter;
		return outputTableReader;
	}

	@Override
	public void simulateDevelopment() {
		// ZoningRulesI.openLogFile(logFilePath);
		// land.getDevelopmentLogger().open(logFilePath);

		final boolean prepareEstimationData = ResourceUtil.getBooleanProperty(rbSD,
				"PrepareEstimationDataset", false);
		EstimationDataSet eDataSet = null;
		if (prepareEstimationData) {
			final String estimationFileNamePath = ResourceUtil.checkAndGetProperty(
					rbSD, "EstimationDatasetFileNameAndPath");
			final double sampleRatio = ResourceUtil.getDoubleProperty(rbSD,
					"SampleRatio");
			eDataSet = new EstimationDataSet(estimationFileNamePath, sampleRatio);
		}

		final boolean ignoreErrors = ResourceUtil.getBooleanProperty(rbSD,
				"IgnoreErrors", false);
		if (ignoreErrors) {
			final String path = ResourceUtil.checkAndGetProperty(rbSD,
					"AAResultsDirectory");
			// create the logger here

		}

		ZoningRulesI currentZoningRule = null;
		land.setToBeforeFirst();
		long parcelCounter = 0;
		if (prepareEstimationData) {
			// grab all development permit records into the SimpleORM Cache
			land.getSession().query(
					new SQuery<ObservedDevelopmentEvents>(
							ObservedDevelopmentEvents_gen.meta));
		}
		while (land.advanceToNext()) {
			parcelCounter++;
			if (parcelCounter % 1000 == 0) {
				logger.info("finished gridcell " + parcelCounter);
			}
			currentZoningRule = ZoningRulesI.getZoningRuleByZoningRulesCode(
					land.getSession(), land.getZoningRulesCode());
			if (currentZoningRule == null) {
				land.getDevelopmentLogger().logBadZoning(land);
			}
			else {
				if (prepareEstimationData) {

					// Keeping the csv file opened for the whole period of the
					// run might not be a good idea.
					eDataSet.compileEstimationRow(land);
					eDataSet.writeEstimationRow();
				}
				else {
					currentZoningRule.simulateDevelopmentOnCurrentParcel(land,
							ignoreErrors);
				}
			}
		}
		if (prepareEstimationData) {
			eDataSet.close();
		}

		land.getDevelopmentLogger().flush();
		land.addNewBits();

		land.getDevelopmentLogger().close();
	}

	public void calibrateModel(EstimationReader reader, int baseY, int currentY,
			double epsilon, int maxits) {
		// ZoningRulesI.openLogFile(logFilePath);
		baseYear = baseY;
		currentYear = currentY;
		rbSD = ResourceUtil.getResourceBundle("sd");
		try {
			setUp();
			initZoningScheme(currentY, baseY);

			final List<EstimationTarget> targets = reader.readTargets();

			final Matrix targetVariance = new DenseMatrix(
					reader.readTargetVariance(targets));

			final List<Coefficient> coeffs = reader.readCoeffs();

			final Vector means = new DenseVector(reader.readPriorMeans(coeffs));

			final Matrix variance = new DenseMatrix(reader.readPriorVariance(coeffs));

			final Vector epsilons = new DenseVector(means);
			for (int i = 0; i < means.size(); i++) {
				epsilons.set(i, Math.max(Math.abs(means.get(i)), epsilon) * epsilon);
			}

			ZoningRulesI.ignoreErrors = ResourceUtil.getBooleanProperty(rbSD,
					"IgnoreErrors", false);
			final double initialStepSize = ResourceUtil.getDoubleProperty(rbSD,
					"InitialLambda", 600);

			final DifferentiableModel theModel = new ExpectedTargetModel(coeffs, land);
			final GaussBayesianObjective theObjective = new GaussBayesianObjective(
					theModel, coeffs, targets, targetVariance, means, variance);

			// DEBUG
			// This section prints out targets with perturbation so that we can
			// check numerical derivatives.
			/*
			 * double delta = 0.01; Vector targetValues; BufferedWriter writer = null;
			 * try { writer = new BufferedWriter(new FileWriter("perturbed.csv")); //
			 * Write target names across the top. for(EstimationTarget target:
			 * targets) writer.write("," + target.getName()); writer.newLine(); //
			 * Write unperturbed target values. try { targetValues =
			 * theModel.getTargetValues(targets, means); writer.write("Unperturbed");
			 * for(int i = 0; i < targetValues.size(); i++) writer.write("," +
			 * targetValues.get(i)); writer.newLine(); } catch(OptimizationException
			 * e) {}
			 * 
			 * // Perturb each coefficient in turn. for(int i = 0; i < means.size();
			 * i++) { Vector perturbed = means.copy(); perturbed.set(i,
			 * perturbed.get(i) + delta); try { targetValues =
			 * theModel.getTargetValues(targets, perturbed);
			 * writer.write(coeffs.get(i).getName()); for(int j = 0; j <
			 * targetValues.size(); j++) writer.write("," + targetValues.get(j));
			 * writer.newLine(); } catch(OptimizationException e) {} } }
			 * catch(IOException e) {} finally { if(writer != null) try {
			 * writer.close(); } catch(IOException e) {} }
			 */
			try {
				land.getSession(); // opens up the session and begins a
				// transaction
				final MarquardtMinimizer theMinimizer = new MarquardtMinimizer(
						theObjective, new DenseVector(reader.readStartingValues(coeffs)));
				theMinimizer.setInitialMarquardtFactor(initialStepSize);
				final double initialObj = theMinimizer.getCurrentObjectiveValue();

				// Clear out the output folders to prepare for new output.
				empty(new File("derivs"));
				empty(new File("gradient"));
				empty(new File("hessian"));
				empty(new File("params"));
				empty(new File("targobj"));

				final MarquardtMinimizer.BeforeIterationCallback before = new MarquardtMinimizer.BeforeIterationCallback() {
					@Override
					public void startIteration(int iteration) {
						// Write out derivative matrix
						BufferedWriter dWriter = null;
						BufferedWriter gWriter = null;
						BufferedWriter hWriter = null;
						try {
							dWriter = new BufferedWriter(new FileWriter("derivs/derivs"
									+ iteration + ".csv"));
							theObjective.printCurrentDerivatives(dWriter);
							gWriter = new BufferedWriter(new FileWriter("gradient/gradient"
									+ iteration + ".csv"));
							theObjective.printGradient(gWriter);
							hWriter = new BufferedWriter(new FileWriter("hessian/hessian"
									+ iteration + ".csv"));
							theObjective.printHessian(hWriter);
						}
						catch (final IOException e) {
						}
						finally {
							if (dWriter != null) {
								try {
									dWriter.close();
								}
								catch (final IOException e) {
								}
							}
							if (gWriter != null) {
								try {
									gWriter.close();
								}
								catch (final IOException e) {
								}
							}
							if (hWriter != null) {
								try {
									hWriter.close();
								}
								catch (final IOException e) {
								}
							}
						}
					}
				};

				final MarquardtMinimizer.AfterIterationCallback after = new MarquardtMinimizer.AfterIterationCallback() {
					@Override
					public void finishedIteration(int iteration) {
						BufferedWriter pWriter = null;
						BufferedWriter tWriter = null;

						try {
							pWriter = new BufferedWriter(new FileWriter("params/params"
									+ iteration + ".csv"));
							theObjective.printParameters(pWriter);
							tWriter = new BufferedWriter(new FileWriter("targobj/targobj"
									+ iteration + ".csv"));
							theObjective.printTargetAndObjective(tWriter);
						}
						catch (final IOException e) {
							logger.error(e.getMessage());
						}
						finally {
							if (pWriter != null) {
								try {
									pWriter.close();
								}
								catch (final IOException e) {
								}
							}
							if (tWriter != null) {
								try {
									tWriter.close();
								}
								catch (final IOException e) {
								}
							}
						}
						land.commitAndStayConnected();
						// land.commit();
						// caused all of our objects to be destroyed
						// so leave it all open for now, in transaction
					}
				};
				final Vector optimalParameters = theMinimizer.iterateToConvergence(
						epsilons, maxits, before, after);
				final Vector optimalTargets = theModel.getTargetValues(targets,
						optimalParameters);
				final String paramsAsString = Arrays.toString(Matrices
						.getArray(optimalParameters));
				if (theMinimizer.lastRunConverged()) {
					logger.info("SD parameter estimation converged on a solution: "
							+ paramsAsString);
					logger.info("Initial objective function = " + initialObj);
					logger.info("Optimal objective function = "
							+ theMinimizer.getCurrentObjectiveValue());
					logger.info("Convergence after "
							+ theMinimizer.getNumberOfIterations() + " iterations");
				}
				else {
					final int numits = theMinimizer.getNumberOfIterations();
					logger.info("SD parameter estimation stopped after " + numits
							+ " iteration" + (numits == 1 ? "" : "s")
							+ " without finding a solution");
					logger.info("Current parameter values: " + paramsAsString);
					if (theMinimizer.lastRunMaxIterations()) {
						logger.info("Reason: stopped at maximum allowed iterations");
					}
					else {
						logger.info("Reason: could not find a valid next iteration");
					}
					logger.info("Initial objective function = " + initialObj);
					logger.info("Optimal objective function = "
							+ theMinimizer.getCurrentObjectiveValue());
				}
				logger.info("Target values at optimum: "
						+ Arrays.toString(Matrices.getArray(optimalTargets)));
			}
			catch (final OptimizationException e) {
				logger.error("Bad initial guess: "
						+ Arrays.toString(Matrices.getArray(means)));
			}
		}
		finally {
			if (land != null) {
				land.disconnect();
			}
		}
	}

	private void empty(File dir) {
		if (dir.exists()) {
			for (final File sub : dir.listFiles()) {
				sub.delete();
			}
		}
		else {
			dir.mkdir();
		}
	}
}