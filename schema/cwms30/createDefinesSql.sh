#!/bin/bash

#############################################################################
# This software was written by Cove Software, LLC ("COVE") under contract 
# to the United States Government. 
# No warranty is provided or implied other than specific contractual terms
# between COVE and the U.S. Government
# 
# Copyright 2014 U.S. Army Corps of Engineers, Hydrologic Engineering Center.
# All rights reserved.
#############################################################################

DH=$DCSTOOL_HOME

if [ -z "$DH" ]
then
	echo "You must defined the environment variable DCSTOOL_HOME before running this script."
	exit 1
fi

echo -n "IMPORTANT! Have you reviewed all settings in 'defines.sh' (y/n) ?"
read answer
if [ "$answer" != "y" ] && [ "$answer" != "Y" ]
then
  echo "Before running this script, edit defines.sh."
  exit
fi

#
# Source the defines file
#
if [ ! -f defines.sh ]
then
  echo "There is no 'defines.sh' in the current directory."
  echo "CD to the CCP's cwms30 schema directory before running this script."
  exit
fi
. defines.sh

echo "Creating defines.sql"
echo "-- defines.sql" >defines.sql
echo "-- Created automatically from 'defines.sh'. " >>defines.sql
echo "-- DO NOT EDIT THIS FILE" >>defines.sql
echo >>defines.sql
echo "undefine LOG;" >>defines.sql
echo "define LOG = $LOG;" >>defines.sql
echo "undefine TBL_SPACE_DIR;" >>defines.sql
echo "define TBL_SPACE_DIR = $TBL_SPACE_DIR;" >>defines.sql
echo "undefine TBL_SPACE_DATA;" >>defines.sql
echo "define TBL_SPACE_DATA = $TBL_SPACE_DATA;" >>defines.sql
echo "undefine TBL_SPACE_TEMP;" >>defines.sql
echo "define TBL_SPACE_TEMP = $TBL_SPACE_TEMP;" >>defines.sql
echo "undefine TBL_SPACE_SPEC;" >>defines.sql
echo "define TBL_SPACE_SPEC = 'tablespace $TBL_SPACE_DATA'" >>defines.sql
echo "undefine CCP_SCHEMA;" >>defines.sql
echo "define CCP_SCHEMA = $CCP_SCHEMA;" >>defines.sql
echo "undefine CCP_PASSWD;" >>defines.sql
echo "define CCP_PASSWD = $CCP_PASSWD;" >>defines.sql
echo "undefine USER_SCHEMA;" >>defines.sql
echo "define USER_SCHEMA = $USER_SCHEMA;" >>defines.sql
echo "undefine USER_PASSWD;" >>defines.sql
echo "define USER_PASSWD = $USER_PASSWD;" >>defines.sql
echo "define CWMS_SCHEMA = $CWMS_SCHEMA;" >>defines.sql
echo "define DBSUPER = $DBSUPER;" >>defines.sql
echo "define dflt_office_code = sys_context('CCPENV','CCP_OFFICE_CODE');" >>defines.sql
echo "define DEFAULT_OFFICE_ID = '$DEFAULT_OFFICE_ID'" >> defines.sql

echo "The file 'defines.sql' has been created. You may now continue the installation."