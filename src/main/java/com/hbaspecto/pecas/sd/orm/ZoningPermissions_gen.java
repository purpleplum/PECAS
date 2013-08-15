package com.hbaspecto.pecas.sd.orm;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import simpleorm.dataset.*;
import simpleorm.utils.*;
import simpleorm.sessionjdbc.SSessionJdbc;
import com.hbaspecto.pecas.sd.SpaceTypesI;
import com.hbaspecto.pecas.sd.ZoningRulesI;
import com.hbaspecto.pecas.sd.ZoningPermissions;
import com.pb.common.util.ResourceUtil;

/**	Base class of table zoning_permissions.<br>
*Do not edit as will be regenerated by running SimpleORMGenerator
*Generated on Fri Sep 25 16:13:29 MDT 2009
***/
public abstract class ZoningPermissions_gen extends SRecordInstance implements java.io.Serializable {
	
   Logger logger = Logger.getLogger(ZoningPermissions_gen.class);

   public static SRecordMeta <ZoningPermissions> meta;

//Columns in table
   public static  SFieldInteger ZoningRulesCode;

   public static  SFieldInteger SpaceTypeId;

   public static  SFieldDouble MinIntensityPermitted;

   public static  SFieldDouble MaxIntensityPermitted;

   public static  SFieldBooleanBit AcknowledgedUse;

   public static  SFieldDouble PenaltyAcknowledgedSpace;

   public static  SFieldDouble PenaltyAcknowledgedLand;

   public static  SFieldInteger ServicesRequirement;
   
   public ZoningPermissions_gen() {
	   if (meta==null) {
		   String msg = ZoningPermissions_gen.class.getName()+" was not initialized for ORM";
		   logger.fatal(msg);
		   throw new RuntimeException(msg);		   
	   }
   }

   public static void init(ResourceBundle rb) {

	   meta = new SRecordMeta<ZoningPermissions>(ZoningPermissions.class, ResourceUtil.getProperty(rb, "sdorm.zoning_permissions", "zoning_permissions"));

	   //Columns in table
	   ZoningRulesCode =
			   new SFieldInteger(meta, ResourceUtil.getProperty(rb, "sdorm.zoning_permissions.zoning_rules_code", "zoning_rules_code"),
					   new SFieldFlags[] { SFieldFlags.SPRIMARY_KEY, SFieldFlags.SMANDATORY });

	   SpaceTypeId =
			   new SFieldInteger(meta, ResourceUtil.getProperty(rb, "sdorm.zoning_permissions.space_type_id", "space_type_id"),
					   new SFieldFlags[] { SFieldFlags.SPRIMARY_KEY, SFieldFlags.SMANDATORY });

	   MinIntensityPermitted =
			   new SFieldDouble(meta, ResourceUtil.getProperty(rb, "sdorm.zoning_permissions.min_intensity_permitted", "min_intensity_permitted"));

	   MaxIntensityPermitted =
			   new SFieldDouble(meta, ResourceUtil.getProperty(rb, "sdorm.zoning_permissions.max_intensity_permitted", "max_intensity_permitted"));

	   AcknowledgedUse =
			   new SFieldBooleanBit(meta, ResourceUtil.getProperty(rb, "sdorm.zoning_permissions.acknowledged_use", "acknowledged_use"));

	   PenaltyAcknowledgedSpace =
			   new SFieldDouble(meta, ResourceUtil.getProperty(rb, "sdorm.zoning_permissions.penalty_acknowledged_space", "penalty_acknowledged_space"));

	   PenaltyAcknowledgedLand =
			   new SFieldDouble(meta, ResourceUtil.getProperty(rb, "sdorm.zoning_permissions.penalty_acknowledged_land", "penalty_acknowledged_land"));

	   ServicesRequirement =
			   new SFieldInteger(meta, ResourceUtil.getProperty(rb, "sdorm.zoning_permissions.services_requirement", "services_requirement"));

   }

   //Column getters and setters
   public int get_ZoningRulesCode(){ return getInt(ZoningRulesCode);}
   public void set_ZoningRulesCode( int value){setInt( ZoningRulesCode,value);}

   public int get_SpaceTypeId(){ return getInt(SpaceTypeId);}
   public void set_SpaceTypeId( int value){setInt( SpaceTypeId,value);}

   public double get_MinIntensityPermitted(){ return getDouble(MinIntensityPermitted);}
   public void set_MinIntensityPermitted( double value){setDouble( MinIntensityPermitted,value);}

   public double get_MaxIntensityPermitted(){ return getDouble(MaxIntensityPermitted);}
   public void set_MaxIntensityPermitted( double value){setDouble( MaxIntensityPermitted,value);}

   public boolean get_AcknowledgedUse(){ return getBoolean(AcknowledgedUse);}
   public void set_AcknowledgedUse( boolean value){setBoolean( AcknowledgedUse,value);}

   public double get_PenaltyAcknowledgedSpace(){ return getDouble(PenaltyAcknowledgedSpace);}
   public void set_PenaltyAcknowledgedSpace( double value){setDouble( PenaltyAcknowledgedSpace,value);}

   public double get_PenaltyAcknowledgedLand(){ return getDouble(PenaltyAcknowledgedLand);}
   public void set_PenaltyAcknowledgedLand( double value){setDouble( PenaltyAcknowledgedLand,value);}

   public int get_ServicesRequirement(){ return getInt(ServicesRequirement);}
   public void set_ServicesRequirement( int value){setInt( ServicesRequirement,value);}

//Foreign key getters and setters
   public SpaceTypesI get_SPACE_TYPES_I(SSessionJdbc ses){
     try{
/** Old code: 
        return SpaceTypesI.findOrCreate(get_SpaceTypeId());
New code below :**/
        return ses.findOrCreate(SpaceTypesI_gen.meta,new Object[]{ 
        	get_SpaceTypeId(),});
     } catch (SException e) {
        if (e.getMessage().indexOf("Null Primary key") > 0) {
          return null;
        }
        throw e;
     }
   }
   public void set_SPACE_TYPES_I( SpaceTypesI value){
      set_SpaceTypeId( value.get_SpaceTypeId());
   }

   public ZoningRulesI get_ZONING_RULES_I(SSessionJdbc ses){
     try{
/** Old code: 
        return ZoningRulesI.findOrCreate(get_ZoningRulesCode());
New code below :**/
        return ses.findOrCreate(ZoningRulesI_gen.meta,new Object[]{ 
        	get_ZoningRulesCode(),
 });
     } catch (SException e) {
        if (e.getMessage().indexOf("Null Primary key") > 0) {
          return null;
        }
        throw e;
     }
   }
   public void set_ZONING_RULES_I( ZoningRulesI value){
      set_ZoningRulesCode( value.get_ZoningRulesCode());
   }

//Find and create
   public static ZoningPermissions findOrCreate( SSessionJdbc ses ,int _ZoningRulesCode, int _SpaceTypeId ){
      return ses.findOrCreate(meta, new Object[] {new Integer( _ZoningRulesCode), new Integer( _SpaceTypeId)});
   }
   public static ZoningPermissions findOrCreate( SSessionJdbc ses,SpaceTypesI _ref, int _ZoningRulesCode){
      return findOrCreate( ses, _ZoningRulesCode, _ref.get_SpaceTypeId());
   }

   public static ZoningPermissions findOrCreate( SSessionJdbc ses,ZoningRulesI _ref, int _SpaceTypeId){
      return findOrCreate( ses, _ref.get_ZoningRulesCode(), _SpaceTypeId);
   }

   @Override
public SRecordMeta <ZoningPermissions> getMeta() {
       return meta;
   }
}