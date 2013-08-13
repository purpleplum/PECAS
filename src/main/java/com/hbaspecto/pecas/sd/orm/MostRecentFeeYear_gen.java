package com.hbaspecto.pecas.sd.orm;
import simpleorm.dataset.*;
import simpleorm.utils.*;
import simpleorm.sessionjdbc.SSessionJdbc;
import java.math.BigDecimal;
import java.util.Date;

/**	Base class of table most_recent_fee_year.<br>
*Do not edit as will be regenerated by running SimpleORMGenerator
*Generated on Fri Sep 25 16:13:29 MDT 2009
***/
abstract class MostRecentFeeYear_gen extends SRecordInstance implements java.io.Serializable {

   public static final SRecordMeta <MostRecentFeeYear> meta = new SRecordMeta<MostRecentFeeYear>(MostRecentFeeYear.class, "most_recent_fee_year");

//Columns in table
   public static final SFieldLong PecasParcelNum =
      new SFieldLong(meta, "pecas_parcel_num");

   public static final SFieldInteger CurrentFeeYear =
      new SFieldInteger(meta, "current_fee_year");

//Column getters and setters
   public long get_PecasParcelNum(){ return getLong(PecasParcelNum);}
   public void set_PecasParcelNum( long value){setLong( PecasParcelNum,value);}

   public int get_CurrentFeeYear(){ return getInt(CurrentFeeYear);}
   public void set_CurrentFeeYear( int value){setInt( CurrentFeeYear,value);}

//Find and create
   public static MostRecentFeeYear findOrCreate( SSessionJdbc ses  ){
      return ses.findOrCreate(meta, new Object[] {});
   }
   public SRecordMeta <MostRecentFeeYear> getMeta() {
       return meta;
   }
}
