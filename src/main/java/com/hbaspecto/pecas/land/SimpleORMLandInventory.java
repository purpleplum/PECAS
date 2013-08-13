package com.hbaspecto.pecas.land;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;
import javax.sql.DataSource;

import org.apache.log4j.Logger;

import simpleorm.dataset.SDataSet;
import simpleorm.dataset.SQuery;
import simpleorm.sessionjdbc.SSessionJdbc;
import simpleorm.utils.SLog;
import simpleorm.utils.SLogLog4j;

import com.hbaspecto.pecas.PECASDataSource;
import com.hbaspecto.pecas.sd.DevelopmentLog;
import com.hbaspecto.pecas.sd.SpaceTypesI;
import com.hbaspecto.pecas.sd.ZoningRulesI;
import com.hbaspecto.pecas.sd.orm.LocalEffectDistances;
import com.hbaspecto.pecas.sd.orm.LocalEffectParameters;
import com.hbaspecto.pecas.sd.orm.LocalEffects;
import com.hbaspecto.pecas.sd.orm.SpaceToCommodity;
import com.hbaspecto.pecas.sd.orm.SpaceTypesGroup;
import com.hbaspecto.pecas.sd.orm.SpaceTypesI_gen;
import com.ibm.xml.parser.Util;
import com.pb.common.datafile.JDBCTableReader;
import com.pb.common.datafile.TableDataFileReader;
import com.pb.common.datafile.TableDataFileWriter;
import com.pb.common.datafile.TableDataSet;
import com.pb.common.sql.JDBCConnection;
import com.pb.common.util.ResourceUtil;

// What is the purpose of this class is it a replacement for PostgreSQL or AbstractSQL inventoy or what?
public abstract class SimpleORMLandInventory implements LandInventory  {

	@Override
	public String parcelToString() {
		if (currentParcel!=null) return currentParcel.get_ParcelId();
		return "(No current parcel)";
	}


	static Logger logger = Logger.getLogger(SimpleORMLandInventory.class);

	private ParcelErrorLog parcelErrorLog=null;

	protected SSessionJdbc session;

	ParcelsTemp currentParcel;      
	private List<ParcelsTemp> parcels;
	Iterator<ParcelsTemp> parcelsIterator; 

	int currentZone;
	Iterator<Integer> tazNumbersIterator;    

	private LoadingQueue<QueueItem<ParcelsTemp>> parcelsInQueue; 
	private LoadingQueue<QueueItem<LocalEffectDistances>> localEffectsQueue;
	private int queueSize;

	private QueueItem<ParcelsTemp> parcelsQueueItem;
	private QueueItem<LocalEffectDistances> localEffectQueueItem;



	/**
	 * This valrable is used to keep track of the maximum value of PECAS_parcel_num
	 * this is value is assigned to the new parcel produced when calling splitParcel() method
	 */   
	protected Long minPecasParcelNum ,maxPecasParcelNum;

	private String landDatabaseDriver 
	,landDatabaseSpecifier
	,user 
	,password
	,schema;

	private static class ParcelsInTazFetcher implements Runnable{
		private LoadingQueue<QueueItem<ParcelsTemp>> queue;
		private Iterator<Integer> tazNumbersIterator;
		public ParcelsInTazFetcher(LoadingQueue<QueueItem<ParcelsTemp>> loadingQueue, ArrayList<Integer> tazNumbers){
			this.queue = loadingQueue;
			this.tazNumbersIterator = tazNumbers.iterator();
		}

		@Override		
		public void run(){
			SSessionJdbc parcelsInTazSession = SimpleORMLandInventory.prepareAdditionalSimpleORMSession("ParcelsInTazSession");
			SDataSet ds;
			while (tazNumbersIterator.hasNext())
			{
				int currentZone = tazNumbersIterator.next().intValue();
				logger.info("Now loading parcels with currentZone = "+currentZone);		    			    	
				List<ParcelsTemp> parcelsInTaz = ParcelsTemp.getParcelsForTaz(parcelsInTazSession, currentZone);

				ds = detachSession(parcelsInTazSession); 
				//save ds in the nodeClass before putting it in the queue
				QueueItem<ParcelsTemp> item = new QueueItem<ParcelsTemp>(ds, parcelsInTaz);

				try {
					queue.put(item);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				parcelsInTazSession.begin(new SDataSet());
			}
			queue.finished=true;
		}		
	}

	private static class LocalEffectsInTazFetcher implements Runnable{ 
		private LoadingQueue<QueueItem<LocalEffectDistances>> queue;
		private Iterator<Integer> tazNumbersIterator;
		public SSessionJdbc localEffectSession;

		public LocalEffectsInTazFetcher(LoadingQueue<QueueItem<LocalEffectDistances>> loadingQueue, ArrayList<Integer> tazNumbers){
			this.queue = loadingQueue;
			this.tazNumbersIterator = tazNumbers.iterator();
		}

		@Override		
		public void run() {		
			localEffectSession= SimpleORMLandInventory.prepareAdditionalSimpleORMSession("LocalEffectSession");
			//LocalEffectSession = SimpleORMLandInventory.prepareAdditionalSimpleORMSession();
			SDataSet ds;
			while (tazNumbersIterator.hasNext())
			{
				int currentZone = tazNumbersIterator.next().intValue();
				logger.info("Now loading the local effect distances for currentZone = "+currentZone);		    			    	
				List<LocalEffectDistances> localEffectDistances = LocalEffectDistances.getLocalEffectDistancesForTaz(localEffectSession,currentZone);

				ds = detachSession(localEffectSession);
				/// Comments on why we keep a dataset() obj in the queue: Two reasons:
				/// 1. To be able to query this dataset direclty, becasue we know already that the dataset has the records we need.
				/// 2. For clean up purposes: we want to destroy the record after we finish using it.
				QueueItem<LocalEffectDistances> item = new QueueItem<LocalEffectDistances>(ds, localEffectDistances);

				try {
					queue.put(item);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				localEffectSession.begin(new SDataSet());
			}
			queue.finished=true;
		}		
	}

	private static class RandomParcelsFetcher implements Runnable{
		private LoadingQueue<QueueItem<ParcelsTemp>> queue;
		private int minRandNum, maxRandNum; 
		public RandomParcelsFetcher(LoadingQueue<QueueItem<ParcelsTemp>> loadingQueue, int randNumRangeStart, int randNumRangeEnd){
			this.queue = loadingQueue;		
			this.minRandNum = randNumRangeStart;
			this.maxRandNum = randNumRangeEnd;
		}

		@Override		
		public void run(){
			SSessionJdbc randomParcelsSession = SimpleORMLandInventory.prepareAdditionalSimpleORMSession("RandomParcelsSession");
			SDataSet ds;
			for(int currentNumber = minRandNum; currentNumber <=maxRandNum; currentNumber++)
			{				
				logger.info("Now loading parcels with currentNumber = "+currentNumber);		    			    	
				List<ParcelsTemp> randomParcels = ParcelsTemp.getParcelsWithRandomNumber(randomParcelsSession, currentNumber);

				ds = detachSession(randomParcelsSession); 
				//save ds in the nodeClass before putting it in the queue
				QueueItem<ParcelsTemp> item = new QueueItem<ParcelsTemp>(ds, randomParcels);

				try {
					queue.put(item);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				randomParcelsSession.begin(new SDataSet());
			}
			queue.finished=true;
		}		
	}
	private static class RandomLocalEffectsFetcher implements Runnable{ 
		private LoadingQueue<QueueItem<LocalEffectDistances>> queue;
		private int minRandNum, maxRandNum;

		public RandomLocalEffectsFetcher(LoadingQueue<QueueItem<LocalEffectDistances>> loadingQueue,int randNumRangeStart, int randNumRangeEnd){
			this.queue = loadingQueue;
			this.minRandNum = randNumRangeStart;
			this.maxRandNum = randNumRangeEnd;
		}

		@Override		
		public void run() {		
			SSessionJdbc localEffectSession= SimpleORMLandInventory.prepareAdditionalSimpleORMSession("RandomLocalEffectSession");
			//LocalEffectSession = SimpleORMLandInventory.prepareAdditionalSimpleORMSession();
			SDataSet ds;
			for(int currentNumber = minRandNum; currentNumber <=maxRandNum; currentNumber++)
			{				
				logger.info("Now loading the local effect distances for currentRandomNumber = "+currentNumber);		    			    	
				List<LocalEffectDistances> localEffectDistances = LocalEffectDistances.getLocalEffectDistancesWithRandomNumber(localEffectSession,currentNumber);

				ds = detachSession(localEffectSession);
				/// Comments on why we keep a dataset() obj in the queue: Two reasons:
				/// 1. To be able to query this dataset direclty, becasue we know already that the dataset has the records we need.
				/// 2. For clean up purposes: we want to destroy the record after we finish using it.
				QueueItem<LocalEffectDistances> item = new QueueItem<LocalEffectDistances>(ds, localEffectDistances);

				try {
					queue.put(item);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				localEffectSession.begin(new SDataSet());
			}
			queue.finished=true;
		}		
	}


	private List<LocalEffectDistances> localEffectDistances;

	private List<LocalEffects> localEffects;

	protected String logFileNameAndPath;

	protected ResourceBundle rbSD;

	protected int numberOfBatches;

	private static Double maxParcelSize;

	private double minParcelSize;

	// This field is used for capacity constraints calculation.
	private int batchCount=1;

	private boolean capacityConstrained;

	public abstract void applyDevelopmentChanges();

	public static SDataSet detachSession(SSessionJdbc session) {
		SDataSet ds;
//		try{				
//			ds = session.detachUnflushedDataSet();	
//		}catch (Exception e){
			ds = session.commitAndDetachDataSet();
//		}
		return ds;
	}

	public SimpleORMLandInventory(){

	}

	public SimpleORMLandInventory(ResourceBundle rb, String landDatabaseDriver, String landDatabaseSpecifier, String user,
			String password, String schema) throws SQLException {

		this.rbSD = rb; 
		this.landDatabaseDriver = landDatabaseDriver;
		this.landDatabaseSpecifier = landDatabaseSpecifier;
		this.user = user;
		this.password = password;
		this.schema = schema;

		initSessionAndBatches();
	}



	/**
	 * @return a SSessionJdbc session. This method begins the session 
	 */
	public static SSessionJdbc prepareSimpleORMSession(ResourceBundle rbSD){
		SLog.setSlogClass(SLogLog4j.class);
		//ResourceBundle rbSD = ResourceUtil.getResourceBundle("sd");

		String landDatabaseDriver = ResourceUtil.getProperty(rbSD,"LandJDBCDriver");
		String landDatabaseSpecifier = ResourceUtil.getProperty(rbSD,"LandDatabase");
		String user = ResourceUtil.getProperty(rbSD,"LandDatabaseUser");
		String password = ResourceUtil.getProperty(rbSD,"LandDatabasePassword");
		//String schema = ResourceUtil.getProperty(rbSD, "schema", "public");
		String schema = ResourceUtil.getProperty(rbSD, "schema");

		SSessionJdbc session =null;
		if (session == null) 
		{    		
			DataSource sDataSource = new PECASDataSource(landDatabaseDriver,landDatabaseSpecifier, user, password);  
			session = SSessionJdbc.open(sDataSource, "Session::ParcelConnection", schema);    	     		
		}    	        
		// begin the session, if it wasn't already begun
		if (!session.hasBegun()) session.begin();

		return session;
	}

	public static SSessionJdbc prepareAdditionalSimpleORMSession(String sessionName){
		SLog.setSlogClass(SLogLog4j.class);

		ResourceBundle rbSD = ResourceUtil.getResourceBundle("sd");

		String landDatabaseDriver = ResourceUtil.getProperty(rbSD,"LandJDBCDriver");
		String landDatabaseSpecifier = ResourceUtil.getProperty(rbSD,"LandDatabase");
		String user = ResourceUtil.getProperty(rbSD,"LandDatabaseUser");
		String password = ResourceUtil.getProperty(rbSD,"LandDatabasePassword");
		//String schema = ResourceUtil.getProperty(rbSD, "schema", "public");
		String schema = ResourceUtil.getProperty(rbSD, "schema");

		DataSource sDataSource = new PECASDataSource(landDatabaseDriver,landDatabaseSpecifier, user, password);           
		SSessionJdbc session = SSessionJdbc.open(sDataSource, "Session::"+sessionName, schema);    	     		

		// begin the session, if it wasn't already begun
		if (!session.hasBegun()) session.begin();

		return session;
	}


	public void setToBeforeFirst(){

		if (!session.hasBegun()) session.begin();

		//numberOfBatches = ResourceUtil.getIntegerProperty(rbSD, "NumberOfBatches",250);

		boolean fetchParcelsByTaz = ResourceUtil.getBooleanProperty(rbSD, "FetchParcelsByTaz",false);
		capacityConstrained = ResourceUtil.getBooleanProperty(rbSD, "CapacityConstrained",true);
		minParcelSize = ResourceUtil.getDoubleProperty(rbSD, "MinParcelSize",400);

		queueSize = ResourceUtil.getIntegerProperty(rbSD, "QueueSize",5);
		parcelsInQueue = new LoadingQueue<QueueItem<ParcelsTemp>>(queueSize);
		localEffectsQueue = new LoadingQueue<QueueItem<LocalEffectDistances>>(queueSize);

		if (!capacityConstrained && fetchParcelsByTaz){
			ArrayList<Integer> tazNumbers = Tazs.getZoneNumbers(session);         	  
			ParcelsInTazFetcher parcelFetcher  = new ParcelsInTazFetcher(parcelsInQueue, tazNumbers);
			Thread tazParcelsFetchingThread = new Thread(parcelFetcher);
			tazParcelsFetchingThread.start();
			//tazParcelsFetchingThread.run();

			LocalEffectsInTazFetcher lefFetcher = new LocalEffectsInTazFetcher(localEffectsQueue, tazNumbers);
			Thread tazLEFetchingThread = new Thread(lefFetcher);
			tazLEFetchingThread.start();
			//tazLEFetchingThread.run();
		} else {
			RandomParcelsFetcher parcelFetcher  = new RandomParcelsFetcher(parcelsInQueue, 1, numberOfBatches);
			Thread randomParcelsFetchingThread = new Thread(parcelFetcher);
			randomParcelsFetchingThread.start();
			//tazParcelsFetchingThread.run();

			RandomLocalEffectsFetcher lefFetcher = new RandomLocalEffectsFetcher(localEffectsQueue, 1, numberOfBatches);
			Thread randomLEFetchingThread = new Thread(lefFetcher);
			randomLEFetchingThread.start();
		}

		try {    	
			parcelsQueueItem = parcelsInQueue.getNext(); 

			if (parcelsQueueItem != null){		
				parcels = parcelsQueueItem.getList();

				localEffectQueueItem = localEffectsQueue.getNext(); 
				localEffectDistances = localEffectQueueItem.getList();
				parcelsIterator = parcels.iterator(); 
			} else {
				logger.fatal("No zone numbers dataset -- perhaps the taz table is empty");
				throw new RuntimeException("No zone numbers dataset -- perhaps the taz table is empty");
			}    		
		} catch (Exception e){
			e.printStackTrace();
			logger.fatal("Problem setting up queue of parcels or local effect distances", e);
			throw new RuntimeException("Problem setting up queue of parcels or local effect distances",e);
		}	
	}

	/*
	protected boolean findNextZoneNumber(){        
		if (tazNumbersIterator == null) {          	             	          
			tazNumbersIterator = Tazs.getZoneNumbers(session).iterator();     
		}    
		if (tazNumbersIterator.hasNext()){
			currentZone = tazNumbersIterator.next().intValue();
			logger.info("Now trying parcels with currentZone = "+currentZone);
			return true;
		} else {
			return false;
		}
	} 
	 */

	public boolean advanceToNext() {
		// remove the old parcel from cache

		if (currentParcel!=null) { 			
			currentParcel.getDataSet().removeRecord(currentParcel);
			//session.getDataSet().removeRecord(currentParcel); 
			currentParcel = null;
		}

		if (parcelsIterator.hasNext()){
			currentParcel = parcelsIterator.next();

			if (currentParcel.get_LandArea() < minParcelSize) {advanceToNext();}
			return true;
		} else {
			logger.info("No more parcels found in the current zone."/*: " + currentZone*/);
			try {   
				// pull the next set of parcels in the queue (i.e. set of parcels in the next zone)
				parcelsQueueItem = parcelsInQueue.getNext(); 				
				System.out.println("Start processing a new item in the queue!!!");
				if (parcelsQueueItem != null){
					//TODO: Do we really need these now
					//remove the previously cached localEffectDistances for that previous zone
					if (localEffectDistances!=null) {							
						localEffectQueueItem.getDataSet().destroy();
						/*
						for (LocalEffectDistances l : localEffectDistances) {
							l.getDataSet().removeRecord(l);
						}
						 */

					}

					//Check if capacity constraints is ON or not.
					if (capacityConstrained){

						batchCount++;						
						updateConstructionCostFactor();	
					}				
					parcels = parcelsQueueItem.getList();				
					localEffectQueueItem = localEffectsQueue.getNext(); 
					System.out.println("Start processing a new LEF item in the queue!!!");
					localEffectDistances = localEffectQueueItem.getList();
					parcelsIterator = parcels.iterator(); 
					return advanceToNext();
				} else {
					logger.info("No more zones found.");
					return false; 
				}    		
			} catch (Exception e){
				// TODO Auto-generated catch block
				e.printStackTrace();
				//System.out.println(e.getMessage());				
				throw new RuntimeException(e);

			}
		}    		       
	}

	/*		
	private List<LocalEffectDistances> cacheLocalEffectDistances(int tazNumber) {
		// forget all the old localEffects, we are done with them
		if (localEffectDistances!=null) {
			for (LocalEffectDistances l : localEffectDistances) {
				session.getDataSet().removeRecord(l);
			}
		}
		// also get all the local effects for the TAZ, so that
		// we get them all in one query
		return LocalEffectDistances.getLocalEffectDistancesForTaz(tazNumber);
	}
	 */

	private void displayRateNumbers(SpaceTypesI spaceType) {
		if (batchCount != 1){
			logger.info("For SpaceType: "+ spaceType.get_SpaceTypeCode());
			double trgt = SpaceTypesGroup.getTargetConstructionQuantity(spaceType.get_SpaceTypeGroupId());
			logger.info("Tagert for the group: " + trgt);

			double rateTarget = trgt/numberOfBatches;
			logger.info("Rate for the tagert: " + rateTarget);

			double constObtained = SpaceTypesGroup.getObtainedConstructionQuantity(spaceType.get_SpaceTypeGroupId());
			logger.info("Obtained Construction: "+ constObtained);

			double rateObtained = constObtained / batchCount;
			logger.info("RateObtained: "+ rateObtained);

			double probChange = (numberOfBatches * rateTarget - batchCount * rateObtained ) 
			/		((numberOfBatches - batchCount) * rateObtained); 
			probChange = Math.min(2, Math.max(probChange,0.5));



			logger.info("Const Utility Adj.: "+
					1 / (spaceType.get_ConstructionCapacityTuningParameter())* Math.log (probChange)
			);
			System.out.println("======================================================");
		}
	}

	private void updateConstructionCostFactor() {
		Collection<SpaceTypesI> list = SpaceTypesI.getAllSpaceTypes();
		Iterator<SpaceTypesI> itr = list.iterator();
		while (itr.hasNext()){
			SpaceTypesI spaceType = itr.next();	
			displayRateNumbers(spaceType);
			spaceType.setUtilityConstructionAdjustment(spaceType.getUtilityConstructionAdjustment()+getConstructionUtilityAdjustment(spaceType));
			double updatedCostFact = spaceType.get_CostAdjustmentFactor() 
			- (1 / (spaceType.get_ConstructionCapacityTuningParameter() * getTrCostRep(spaceType) * spaceType.get_CostAdjustmentFactor() ) )
			* getAdjDampingFactor(spaceType) 
			* getConstructionUtilityAdjustment(spaceType) ;
			spaceType.set_CostAdjustmentFactor(updatedCostFact);
		}
	}

	/**
	 * @param spaceType 
	 * @return
	 */
	public double getConstructionUtilityAdjustment(SpaceTypesI spaceType) {
		if (batchCount==1) return 0;
		double trgt = SpaceTypesGroup.getTargetConstructionQuantity(spaceType.get_SpaceTypeGroupId());
		double rateTarget = trgt/numberOfBatches;
		double rateObtained = SpaceTypesGroup.getObtainedConstructionQuantity(spaceType.get_SpaceTypeGroupId()) / batchCount;

		double probChange = (numberOfBatches * rateTarget - batchCount * rateObtained ) 
		/		((numberOfBatches - batchCount) * rateObtained); 
		probChange = Math.min(2, Math.max(probChange,0.5));

		return 1 / (spaceType.get_ConstructionCapacityTuningParameter())* Math.log (probChange);
	}

	/**
	 * @param spaceType
	 * @return The representative average unfactored costs for all potential transitions to update space type per unit of space 
	 * across the set of parcels considered in the n batch processed up to this point. 
	 */
	private double getTrCostRep(SpaceTypesI spaceType) {
		return (spaceType.cumulativeCostForAdd + spaceType.cumulativeCostForDevelopNew)/(spaceType.numberOfParcelsConsideredForAdd + spaceType.numberOfParcelsConsideredForDevelopNew);
	}

	private double getAdjDampingFactor(SpaceTypesI spaceType) {
		int spaceGroupID = spaceType.get_SpaceTypeGroupId();
		return SpaceTypesGroup.getSpaceTypeGroupByID(spaceGroupID).get_CostAdjustmentDampingFactor();
	}

	public String getParcelId() {    	
		return currentParcel.get_ParcelId();
	}
	public int getCoverage() {
		return currentParcel.get_SpaceTypeId();
	}

	public double getQuantity() {
		return currentParcel.get_SpaceQuantity();
	}

	public int getAvailableServiceCode() { 
		// TODO build x-ref tables for servicing by year, so that user can specify future service coverage
		return currentParcel.get_AvailableServicesCode();
	}

	@Override
	public boolean isBrownfield() {
		return currentParcel.get_IsBrownfield();
	}

	@Override
	public boolean isDerelict() {
		return currentParcel.get_IsDerelict();
	}

	public double getLandArea() {
		return currentParcel.get_LandArea();
	}

	public int getYearBuilt() {
		return currentParcel.get_YearBuilt();
	}

	public boolean isDevelopable() {
		return true;
	}

	public void putCoverage(int coverageId) {
		currentParcel.set_SpaceTypeId(coverageId);
	}

	public void putQuantity(double quantity) {
		currentParcel.set_SpaceQuantity(quantity);
	}

	public void putDerelict(boolean isDerelict) {
		currentParcel.set_IsDerelict(isDerelict);
	}

	public void putBrownfield(boolean isBrownfield) {
		currentParcel.set_IsBrownfield(isBrownfield);
	}

	public void putYearBuilt(int yearBuilt) {
		currentParcel.set_YearBuilt(yearBuilt);
	}

	@Override
	public void putAvailableServiceCode(int service) {
		currentParcel.set_AvailableServicesCode(service);	
	}

	public ParcelInterface splitParcel(double newLandSize)
	throws NotSplittableException {

		double size = currentParcel.get_LandArea();
		if (size <= newLandSize) {
			logger.fatal("Tried to split off "+newLandSize+" off a parcel that is only of size "+size);
			throw new NotSplittableException("Tried to split off "+newLandSize+" off a parcel that is only of size "+size);
		}

		++maxPecasParcelNum;
		TempParcelForSplitting newOne =  new TempParcelForSplitting(currentParcel, maxPecasParcelNum);

		double quantity = currentParcel.get_SpaceQuantity();       
		double portionToNew = newLandSize/size;		

		currentParcel.set_LandArea(size-newLandSize); 
		currentParcel.set_SpaceQuantity(currentParcel.get_SpaceQuantity()*(1-portionToNew));
		newOne.set_LandArea(newLandSize);
		newOne.set_SpaceQuantity(quantity*portionToNew);

		return newOne;        
	}

	public void addNewBits() {
		// Leave it. empty method for now because pseudo parcels will be added by the update query in ApplyDevelopmentChanges().

	}

	@Override
	public TableDataSet summarizeInventory() {
		logger.info("Using predefined database query to summarize floorspace (reading from FloorspaceI_view)");
		return readInventoryTable("floorspacei_view");
	}

	@Override
	public double getMaxParcelSize() {
		//getMaxParcelSize(). It returns +infinity now;		
		if (maxParcelSize == null){
			maxParcelSize = ResourceUtil.getDoubleProperty(rbSD, "MaxParcelSize", Double.POSITIVE_INFINITY);
		} 
		return maxParcelSize.doubleValue();
	}

	//@Override
	public long getPECASParcelNumber() {
		return currentParcel.get_PecasParcelNum();
	}

	@Override
	public double getPrice(int coverageCode, int currentYear, int baseYear) {
		int taz = currentParcel.get_Taz();
		int luz = Tazs.getTazRecord(taz).get_LuzNumber();
		List<SpaceToCommodity> commodities = SpaceToCommodity.getCommoditiesForSpaceType(coverageCode);
		double totalWeight = 0;
		for (SpaceToCommodity stc : commodities) {
			totalWeight += stc.get_Weight();
		}
		double rent = 0;
		for (SpaceToCommodity stc: commodities) {
			ExchangeResults er = null;
			try {
			er = session.mustFind(ExchangeResults.meta, stc.get_AaCommodity(), luz);
			} catch (RuntimeException e) {
				// Place to set a breakpoint;
				throw new RuntimeException("Can't find price for \""+stc.get_AaCommodity()+"\" in luz "+luz,e);
			}
			rent += stc.get_Weight() * er.get_Price()/totalWeight; 
		}

		// ENHANCEMENT use database to filter for most current year, so that SD doesn't need to worry about the year here.
		rent = applyParcelSpecificRentModifiers(currentParcel, rent, coverageCode, currentYear, baseYear);
		return rent;
	}

	private double applyParcelSpecificRentModifiers(ParcelsTemp currentParcel2, double rent, int coverageCode, int year, int baseYear) {		
		for (LocalEffects l : getLocalEffects()) {
			if (l.isRentLocalEffect()) {
				LocalEffectParameters lefp = LocalEffectParameters.findInCache(l.get_LocalEffectId(), coverageCode);
				if (lefp != null) {
					rent = modifyValueForParcelEffects(rent, year, baseYear, l,
							lefp);
				}
			}
		}
		return rent;
	}


	double applyParcelSpecificCostModifiers(ParcelsTemp currentParcel2, double cost, int coverageCode, int year, int baseYear) {		
		for (LocalEffects l : getLocalEffects()) {
			if (l.isCostLocalEffect()) {
				LocalEffectParameters lefp = LocalEffectParameters.findInCache(l.get_LocalEffectId(), coverageCode);
				if (lefp != null) {
					cost = modifyValueForParcelEffects(cost, year, baseYear, l,
							lefp);
				}
			}
		}
		return cost;
	}

	private double modifyValueForParcelEffects(double valueToBeModified,
			int year, int baseYear, LocalEffects l, LocalEffectParameters lefp) {
		LocalEffectDistances lef = null;
		// ENHANCEMENT use database to filter for max(year<current) instead of doing it here in this loop.
		for (int tryYear = year; tryYear >= baseYear; tryYear--) {									
			lef =  localEffectQueueItem.getDataSet().find(LocalEffectDistances.meta, currentParcel.get_PecasParcelNum(), l.get_LocalEffectId(), tryYear);
			if (lef !=null) break;
		}
		if (lef != null)
			valueToBeModified = lefp.applyFunction(valueToBeModified, lef.get_LocalEffectDistance());
		else{
			if (logger.isDebugEnabled()) logger.debug("Can't find local effect distances for "+l.get_LocalEffectName()+" for parcel "+currentParcel.get_PecasParcelNum()+":"+currentParcel.get_ParcelId());
			valueToBeModified = lefp.applyFunctionForMaxDist(valueToBeModified);
		}
		return valueToBeModified;
	}
	
	
	private List<LocalEffects> getLocalEffects() {
		if (localEffects == null) {
			SQuery query = new SQuery(LocalEffects.meta);
			localEffects = session.query(query);
		}
		return localEffects;
	}

	@Override
	public int get_CostScheduleId() {
		return currentParcel.get_CostScheduleId();		 
	}

	@Override
	public int get_FeeScheduleId() {		
		return currentParcel.get_FeeScheduleId();
	}

	public int getZoningRulesCode() {
		return currentParcel.get_ZoningRulesCode();
	}

	@Override
	public int getTaz() {
		return currentParcel.get_Taz();
	}

	public TableDataSet readInventoryTable(String tableName) {    	
		JDBCConnection jdbcConn = new JDBCConnection(landDatabaseSpecifier, landDatabaseDriver, user, password);
		try {
			if (schema!=null) {
				if (schema.equalsIgnoreCase("public") || schema.equalsIgnoreCase("dbo") || schema.equals("")) {
					logger.info("Not setting schema for foorspacei_view, using default"+tableName);
				} else{
					Statement statement = jdbcConn.createStatement();
					statement.execute("set search_path to '"+schema+"';");
				}
			}
			JDBCTableReader reader = new JDBCTableReader(jdbcConn);
			TableDataSet s = reader.readTable(tableName);
			if (s==null){
				logger.fatal("Query "+tableName+" to summarize inventory has problems");
				throw new RuntimeException("Query "+tableName+" to summarize inventory has problems");
			}
			return s;
		} catch (IOException e) {
			logger.fatal("Can't run query "+tableName+" to summarize floorspace inventory");
			throw new RuntimeException("Can't run query "+tableName+" to summarize floorspace inventory",e);
		} catch (SQLException e) {
			logger.fatal("Can't run query "+tableName+" to summarize floorspace inventory");
			throw new RuntimeException("Can't run query "+tableName+" to summarize floorspace inventory",e);
		}
	}

	@Override
	public void setMaxParcelSize(double maxParcelSize) {
		this.maxParcelSize=maxParcelSize;
	}

	public void setDatabaseConnectionParameter(ResourceBundle rb, String landDatabaseDriver,
			String landDatabaseSpecifier, String landDatabaseUser,
			String landDatabasePassword, String schema) {

		this.rbSD = rb;
		this.landDatabaseDriver = landDatabaseDriver;
		this.landDatabaseSpecifier = landDatabaseSpecifier;
		this.user = landDatabaseUser;
		this.password = landDatabasePassword;
		this.schema = schema; 
	}

	protected void initSessionAndBatches(){

		session=prepareSimpleORMSession(rbSD);
		maxPecasParcelNum = Parcels.getMaximumPecasParcelNum(session);	
		numberOfBatches = ResourceUtil.getIntegerProperty(rbSD, "NumberOfBatches",250);
		if (numberOfBatches < 1) {
			logger.error("NumberOfBatches cannot be less than 1 in properties file");
			numberOfBatches=1;
		}
	}

	public void setLogFile(String logFileNameAndPath) {
		this.logFileNameAndPath=logFileNameAndPath;

	}

	@Override
	public void readSpacePrices(TableDataFileReader reader) {
		Statement statement;
		try {
			session.flush();
			statement = session.getJdbcConnection().createStatement();
			String tableName = ExchangeResults.meta.getTableName();
			logger.info("Reading in ExchangeResults.csv");
			statement.execute("TRUNCATE TABLE exchange_results;");
			String path = reader.getMyDirectory();
			String fileName = path + "ExchangeResults.csv";
			TableDataSet prices = reader.readFile(new File(fileName));
			int commodity_col = prices.checkColumnPosition("Commodity");
			int luz_col = prices.checkColumnPosition("ZoneNumber");
			int price_col = prices.checkColumnPosition("Price");
			int internalBought_col = prices.checkColumnPosition("InternalBought");
			for (int row = 1; row<= prices.getRowCount(); row++) {
				String query = "insert into "+tableName+" ("+ExchangeResults.Commodity.getColumnName()+", "+
						ExchangeResults.Luz.getColumnName()+", "+ ExchangeResults.Price.getColumnName()+", internal_bought) values ('" +
				prices.getStringValueAt(row, commodity_col) +"' , "+ 
				prices.getValueAt(row, luz_col) +" , "+
				prices.getValueAt(row, price_col) +" , "+
				prices.getValueAt(row, internalBought_col) +");";
				statement.execute(query);
			}
			session.commit();
		} catch (SQLException e) {
			logger.fatal("Can't read in space prices",e);
			throw new RuntimeException("Can't read in space prices from AA", e);
		} catch (IOException e) {
			logger.fatal("Can't read in space prices",e);
			throw new RuntimeException("Can't read in space prices from AA", e);
		}

	}
	
	@Override
	public void applyPriceSmoothing(TableDataFileReader reader, TableDataFileWriter writer) {
		readSkimDistances(reader);
		double gravityExponent = ResourceUtil.getDoubleProperty(rbSD, "SmoothingExponent", -2.0);
		String query = "update exchange_results\n" +
				"set price=new_price from\n" +
				"( select commodity, origin_luz, sum(internal_bought) as total_bought, " +
				"sum(internal_bought * price * power(distance, " + gravityExponent + ")) / " +
				"sum(internal_bought * power(distance, " + gravityExponent + ")) as new_price\n" +
				"from exchange_results\n" +
				"inner join space_to_commodity\n" +
				"on exchange_results.commodity=space_to_commodity.aa_commodity\n" +
				"inner join distances\n" +
				"on exchange_results.luz=distances.destination_luz\n" +
				"group by commodity, origin_luz\n" +
				") new_prices\n" +
				"where exchange_results.commodity=new_prices.commodity\n" +
				"and exchange_results.luz=new_prices.origin_luz\n" +
				"and total_bought > 0;";
		logger.info(query);
		try {
			session.begin();
			Statement statement = session.getJdbcConnection().createStatement();
			logger.info("Smoothing prices");
			statement.execute(query);
			session.commit();
		} catch(SQLException e) {
			logger.fatal("Can't smooth prices", e);
			throw new RuntimeException("Can't smooth prices", e);
		}
		writeSmoothedPrices(writer, reader.getMyDirectory());
	}
	
	private void readSkimDistances(TableDataFileReader reader) {
		Statement statement;
		String distanceColName = ResourceUtil.checkAndGetProperty(rbSD, "DistanceColumnName");
		try {
			session.begin();
			statement = session.getJdbcConnection().createStatement();
			logger.info("Reading in distance skims");
			statement.execute("TRUNCATE TABLE distances;");
			String path = reader.getMyDirectory();
			String fileName = path + "SkimsForSmoothing.csv";
			TableDataSet distances = reader.readFile(new File(fileName));
			int originCol = distances.checkColumnPosition("Origin");
			int destinationCol = distances.checkColumnPosition("Destination");
			int distanceCol = distances.checkColumnPosition(distanceColName);
			for (int row = 1; row<= distances.getRowCount(); row++) {
				double distance = distances.getValueAt(row, distanceCol);
				if(distance == 0)
					distance = 1E99;
				String query = "insert into distances (origin_luz, destination_luz, distance) values (" +
						distances.getValueAt(row, originCol) + ", " + 
						distances.getValueAt(row, destinationCol) + ", " +
						distance + ");";
				statement.execute(query);
			}
			session.commit();
		} catch (SQLException e) {
			logger.fatal("Can't read in space prices",e);
			throw new RuntimeException("Can't read in space prices from AA", e);
		} catch (IOException e) {
			logger.fatal("Can't read in space prices",e);
			throw new RuntimeException("Can't read in space prices from AA", e);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void writeSmoothedPrices(TableDataFileWriter writer, String path) {
		Statement statement;
		try {
			statement = session.getJdbcConnection().createStatement();
			logger.info("Writing out smoothed prices");
			ResultSet results = statement.executeQuery("select * from exchange_results order by commodity, luz;");
			
			TableDataSet newPrices = new TableDataSet();
			newPrices.appendColumn(new String[0], "Commodity");
			newPrices.appendColumn(new int[0], "ZoneNumber");
			newPrices.appendColumn(new float[0], "Price");
			newPrices.appendColumn(new float[0], "InternalBought");
			
			HashMap row = new HashMap();
			while(results.next()) {
				row.clear();
				row.put("Commodity", results.getString("commodity"));
				row.put("ZoneNumber", results.getFloat("luz"));
				row.put("Price", results.getFloat("price"));
				row.put("InternalBought", results.getFloat("internal_bought"));
				newPrices.appendRow(row);
			}

			String fileName = path + "ExchangeResultsSmooth.csv";
			File file = new File(fileName);
			file.createNewFile();
			writer.writeFile(newPrices, file);
		} catch (SQLException e) {
			logger.fatal("Can't write out smoothed prices",e);
			throw new RuntimeException("Can't write out smoothed prices", e);
		} catch (IOException e) {
			logger.fatal("Can't write out smoothed prices",e);
			throw new RuntimeException("Can't write out smoothed prices", e);
		}
	}

	public ParcelErrorLog getParcelErrorLog(){
		if (parcelErrorLog!=null) return parcelErrorLog;

		parcelErrorLog = new ParcelErrorLog();
		String fileNamePath = ResourceUtil.checkAndGetProperty(rbSD, "LogFilePath") + "parcelsErrorLog.csv";
		parcelErrorLog.open(fileNamePath);
		return parcelErrorLog; 
	}

	@Override
	public void disconnect() {
		if (session !=null) {
			if (session.hasBegun()) {
				session.commit();
			}
			session.close();
		}
	}

	
	
	@Override
	public void commitAndStayConnected() {
		SDataSet s = session.commitAndDetachDataSet();
		session.begin(s);
	}


	@Override
	public void init(int year) {
		initSessionAndBatches();
		boolean createTempParcels = ResourceUtil.getBooleanProperty(rbSD, "CreateTempParcels",true);
		if (createTempParcels) 
			createParcelsTemp(year);

	}

	protected abstract void createParcelsTemp(int year);

	@Override
	public SSessionJdbc getSession() {
		if (!session.hasBegun()) {
			session.begin();
		}
		return session;
	}

}
