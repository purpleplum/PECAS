package com.hbaspecto.pecas.sd.orm;

import java.util.List;

import com.hbaspecto.pecas.land.ParcelsTemp;
import simpleorm.dataset.SFieldReference;
import simpleorm.dataset.SQuery;
import simpleorm.sessionjdbc.SSessionJdbc;

/**Business rules class for table local_effect_distances.<br>
* Will not be regenerated by SimpleORMGenerator, add any business rules to this class
**/

public class LocalEffectDistances extends LocalEffectDistances_gen implements java.io.Serializable {
	
	static final SFieldReference<ParcelsTemp> PARCELSTEMP 
    = new SFieldReference(meta, ParcelsTemp.meta, "pecas_parce_num");

	public static List<LocalEffectDistances> getLocalEffectDistancesForTaz(SSessionJdbc session, int taz) {	
		//SQuery<LocalEffectDistances> query = SQuery<ParcelsTemp>(meta);
		SQuery<LocalEffectDistances> query = new SQuery<LocalEffectDistances>(meta).innerJoin(PARCELSTEMP).eq(ParcelsTemp_gen.Taz, taz);
		List<LocalEffectDistances> list = session.query(query);
		return list;
		
	}
	
	public static List<LocalEffectDistances> getLocalEffectDistancesWithRandomNumber(SSessionJdbc session, int randomNumber) {	
		//SQuery<LocalEffectDistances> query = SQuery<ParcelsTemp>(meta);
		SQuery<LocalEffectDistances> query = new SQuery<LocalEffectDistances>(meta).innerJoin(PARCELSTEMP).eq(ParcelsTemp_gen.Randnum, randomNumber);
		List<LocalEffectDistances> list = session.query(query);
		return list;
		
	}
	
	public static List<LocalEffectDistances> getLocalEffectDistances(){
		SSessionJdbc session = SSessionJdbc.getThreadLocalSession();
		SQuery<LocalEffectDistances> query = new SQuery<LocalEffectDistances>(meta).innerJoin(PARCELSTEMP);
		List<LocalEffectDistances> list = session.query(query);
		return list;
		
	}
	
	/*public static List<MostRecentLocalEffectWithTaz> getMostRecentLocalEffectDistancesForTaz(int taz) {
		SSessionJdbc session = SSessionJdbc.getThreadLocalSession();
		SQuery<MostRecentLocalEffectWithTaz> query = new SQuery<MostRecentLocalEffectWithTaz>(MostRecentLocalEffectWithTaz.meta).eq(MostRecentLocalEffectWithTaz.Taz, taz);
		List<MostRecentLocalEffectWithTaz> list = session.query(query);
		return list;
		
	}*/
	

}
