package com.hbaspecto.pecas.sd.orm;

import java.util.HashMap;
import java.util.List;

import simpleorm.dataset.SQuery;
import simpleorm.sessionjdbc.SSessionJdbc;

/**
 * Business rules class for table space_to_commodity.<br>
 * Will not be regenerated by SimpleORMGenerator, add any business rules to this
 * class
 **/

public class SpaceToCommodity extends SpaceToCommodity_gen implements
		java.io.Serializable {

	private static HashMap<Integer, List<SpaceToCommodity>> commoditiesForSpace = new HashMap<Integer, List<SpaceToCommodity>>();

	public static List<SpaceToCommodity> getCommoditiesForSpaceType(
			int coverageCode) {
		final List<SpaceToCommodity> result = commoditiesForSpace.get(coverageCode);
		if (result != null) {
			return result;
		}
		final SSessionJdbc session = SSessionJdbc.getThreadLocalSession();
		final SQuery<SpaceToCommodity> query = new SQuery<SpaceToCommodity>(
				SpaceToCommodity_gen.meta).eq(SpaceToCommodity_gen.SpaceTypeId,
				coverageCode);
		final List<SpaceToCommodity> commodities = session.query(query);
		commoditiesForSpace.put(coverageCode, commodities);
		return commodities;
	}

}