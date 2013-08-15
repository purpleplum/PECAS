package com.hbaspecto.pecas.sd.orm;
import simpleorm.dataset.*;
import simpleorm.sessionjdbc.SSessionJdbc;

/**	Base class of table space_types_group.<br>
*Do not edit as will be regenerated by running SimpleORMGenerator
*Generated on Wed Dec 23 15:34:48 MST 2009
***/
abstract class SpaceTypesGroup_gen extends SRecordInstance implements java.io.Serializable {

   public static final SRecordMeta <SpaceTypesGroup> meta = new SRecordMeta<SpaceTypesGroup>(SpaceTypesGroup.class, "space_types_group");

//Columns in table
   public static final SFieldInteger SpaceTypesGroupId =
      new SFieldInteger(meta, "space_types_group_id",
         new SFieldFlags[] { SFieldFlags.SPRIMARY_KEY, SFieldFlags.SMANDATORY });

   public static final SFieldString SpaceTypesGroupName =
      new SFieldString(meta, "space_types_group_name", 2147483647);

   public static final SFieldDouble CostAdjustmentDampingFactor =
      new SFieldDouble(meta, "cost_adjustment_damping_factor");

//Column getters and setters
   public int get_SpaceTypesGroupId(){ return getInt(SpaceTypesGroupId);}
   public void set_SpaceTypesGroupId( int value){setInt( SpaceTypesGroupId,value);}

   public String get_SpaceTypesGroupName(){ return getString(SpaceTypesGroupName);}
   public void set_SpaceTypesGroupName( String value){setString( SpaceTypesGroupName,value);}

   public double get_CostAdjustmentDampingFactor(){ return getDouble(CostAdjustmentDampingFactor);}
   public void set_CostAdjustmentDampingFactor( double value){setDouble( CostAdjustmentDampingFactor,value);}

//Find and create
   public static SpaceTypesGroup findOrCreate( SSessionJdbc ses ,int _SpaceTypesGroupId ){
      return ses.findOrCreate(meta, new Object[] {new Integer( _SpaceTypesGroupId)});
   }
   @Override
public SRecordMeta <SpaceTypesGroup> getMeta() {
       return meta;
   }
}
