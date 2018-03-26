---
-- #%L
-- Eureka Protempa ETL
CREATE SCHEMA IF NOT EXISTS "EUREKA";

SET SCHEMA "EUREKA";

CREATE TABLE IF NOT EXISTS "PATIENT" (

"PATIENT_KEY"    NUMBER(22,0) NOT NULL ,
"FIRST_NAME"     VARCHAR2(32) ,
"LAST_NAME"      VARCHAR2(32) ,
"DOB"            DATE         ,
"LANGUAGE"       VARCHAR2(32) ,
"MARITAL_STATUS" VARCHAR2(32) ,
"RACE"           VARCHAR2(32) ,
"GENDER"         VARCHAR2(16) ,

CONSTRAINT patient_pk PRIMARY KEY (patient_key)
);
--tablespace users
--nologging;



CREATE TABLE IF NOT EXISTS "PROVIDER" (

"PROVIDER_KEY"  NUMBER(22,0) NOT NULL ,
"FIRST_NAME"    VARCHAR2(32) ,
"LAST_NAME"     VARCHAR2(32) ,

CONSTRAINT provider_pk PRIMARY KEY (provider_key)
);
--tablespace users
--nologging;


CREATE TABLE IF NOT EXISTS "ENCOUNTER" (

"ENCOUNTER_KEY"   NUMBER(22,0) NOT NULL ,
"PATIENT_KEY"     NUMBER(22,0) NOT NULL ,
"PROVIDER_KEY"    NUMBER(22,0) NOT NULL ,
"TS_START"        TIMESTAMP(4) ,
"TS_END"          TIMESTAMP(4) ,
"ENCOUNTER_TYPE"  VARCHAR2(64) ,
"DISCHARGE_DISP"  VARCHAR2(64) ,
 
CONSTRAINT encounter_pk PRIMARY KEY (encounter_key)
);
--tablespace users
--nologging;
 
 
 
 
 
CREATE TABLE IF NOT EXISTS "CPT_EVENT" (
 
"EVENT_KEY"     VARCHAR2(32) NOT NULL ,
"ENCOUNTER_KEY" NUMBER(22,0) NOT NULL ,
"TS_OBX"        TIMESTAMP(4) ,
"ENTITY_ID"     VARCHAR2(128) NOT NULL ,
 
CONSTRAINT cpt_event_pk PRIMARY KEY (event_key)
);
--tablespace users
--nologging;
 
 
 
CREATE TABLE IF NOT EXISTS "ICD9D_EVENT" (
 
"EVENT_KEY"     VARCHAR2(32) NOT NULL ,
"ENCOUNTER_KEY" NUMBER(22,0) NOT NULL ,
"TS_OBX"        TIMESTAMP(4) ,
"ENTITY_ID"     VARCHAR2(128) NOT NULL ,
"RANK"		NUMBER(22,0) NOT NULL ,
 
CONSTRAINT icd9d_event_pk PRIMARY KEY (event_key)
);
--tablespace users
--nologging;
 
 
 
CREATE TABLE IF NOT EXISTS "ICD9P_EVENT" (
 
"EVENT_KEY"     VARCHAR2(32) NOT NULL ,
"ENCOUNTER_KEY" NUMBER(22,0) NOT NULL ,
"TS_OBX"        TIMESTAMP(4) ,
"ENTITY_ID"     VARCHAR2(128) NOT NULL ,
 
CONSTRAINT icd9p_event_pk PRIMARY KEY (event_key)
);
--tablespace users
--nologging;
 
 
 
 
CREATE TABLE IF NOT EXISTS "MEDS_EVENT" (
 
"EVENT_KEY"     VARCHAR2(32) NOT NULL ,
"ENCOUNTER_KEY" NUMBER(22,0) NOT NULL ,
"TS_OBX"        TIMESTAMP(4) ,
"ENTITY_ID"     VARCHAR2(128) NOT NULL ,
 
CONSTRAINT meds_event_pk PRIMARY KEY (event_key)
);
--tablespace users
--nologging;
 
 
 
 
CREATE TABLE IF NOT EXISTS "LABS_EVENT" (
 
"EVENT_KEY"     VARCHAR2(32) NOT NULL ,
"ENCOUNTER_KEY" NUMBER(22,0) NOT NULL ,
"TS_OBX"        TIMESTAMP(4) ,
"ENTITY_ID"     VARCHAR2(128) NOT NULL ,
"RESULT_STR"    VARCHAR2(32) ,
"RESULT_NUM"    NUMBER(18,4) ,
"UNITS"         VARCHAR2(16) ,
"FLAG"          VARCHAR2(8) ,
 
CONSTRAINT labs_event_pk PRIMARY KEY (event_key)
);
--tablespace users
--nologging;
 
 
 
 
CREATE TABLE IF NOT EXISTS "VITALS_EVENT" (
 
"EVENT_KEY"     VARCHAR2(32) NOT NULL ,
"ENCOUNTER_KEY" NUMBER(22,0) NOT NULL ,
"TS_OBX"        TIMESTAMP(4) ,
"ENTITY_ID"     VARCHAR2(128) NOT NULL ,
"RESULT_STR"    VARCHAR2(32) ,
"RESULT_NUM"    NUMBER(18,4) ,
"UNITS"         VARCHAR2(16) ,
"FLAG"          VARCHAR2(8) ,
 
CONSTRAINT vitals_event_pk PRIMARY KEY (event_key)
);
--tablespace users
--nologging;
