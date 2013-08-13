package com.hbaspecto.pecas.sd.orm;
import simpleorm.dataset.*;
import simpleorm.utils.*;
import simpleorm.sessionjdbc.SSessionJdbc;

import com.hbaspecto.pecas.land.Parcels;
import com.hbaspecto.pecas.sd.ZoningRulesI;

/**	Base class of table parcel_zoning_xref.<br>
*Do not edit as will be regenerated by running SimpleORMGenerator
*Generated on Fri Sep 25 16:13:29 MDT 2009
***/
abstract class ParcelZoningXref_gen extends SRecordInstance implements java.io.Serializable {

   public static final SRecordMeta <ParcelZoningXref> meta = new SRecordMeta<ParcelZoningXref>(ParcelZoningXref.class, "parcel_zoning_xref");

//Columns in table
   public static final SFieldLong PecasParcelNum =
      new SFieldLong(meta, "pecas_parcel_num",
         new SFieldFlags[] { SFieldFlags.SPRIMARY_KEY, SFieldFlags.SMANDATORY });

   public static final SFieldInteger ZoningRulesCode =
      new SFieldInteger(meta, "zoning_rules_code");

   public static final SFieldInteger YearEffective =
      new SFieldInteger(meta, "year_effective",
         new SFieldFlags[] { SFieldFlags.SPRIMARY_KEY, SFieldFlags.SMANDATORY });

//Column getters and setters
   public long get_PecasParcelNum(){ return getLong(PecasParcelNum);}
   public void set_PecasParcelNum( long value){setLong( PecasParcelNum,value);}

   public int get_ZoningRulesCode(){ return getInt(ZoningRulesCode);}
   public void set_ZoningRulesCode( int value){setInt( ZoningRulesCode,value);}

   public int get_YearEffective(){ return getInt(YearEffective);}
   public void set_YearEffective( int value){setInt( YearEffective,value);}

//Foreign key getters and setters
   public Parcels get_PARCELS(SSessionJdbc ses){
     try{
/** Old code: 
        return Parcels.findOrCreate(get_PecasParcelNum());
New code below :**/
        return ses.findOrCreate(Parcels.meta,new Object[]{ 
        	get_PecasParcelNum(),
 });
     } catch (SException e) {
        if (e.getMessage().indexOf("Null Primary key") > 0) {
          return null;
        }
        throw e;
     }
   }
   public void set_PARCELS( Parcels value){
      set_PecasParcelNum( value.get_PecasParcelNum());
   }

   public ZoningRulesI get_ZONING_RULES_I(SSessionJdbc ses){
     try{
/** Old code: 
        return ZoningRulesI.findOrCreate(get_ZoningRulesCode());
New code below :**/
        return ses.findOrCreate(ZoningRulesI.meta,new Object[]{ 
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
   public static ParcelZoningXref findOrCreate( SSessionJdbc ses ,long _PecasParcelNum, int _YearEffective ){
      return ses.findOrCreate(meta, new Object[] {new Long( _PecasParcelNum), new Integer( _YearEffective)});
   }
   public static ParcelZoningXref findOrCreate( SSessionJdbc ses,Parcels _ref, int _YearEffective){
      return findOrCreate( ses, _ref.get_PecasParcelNum(), _YearEffective);
   }

   public SRecordMeta <ParcelZoningXref> getMeta() {
       return meta;
   }
}
