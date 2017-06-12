--*********************************************************************************
-- $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/sql/mysql/archives_recorder.sql $
-- $Id: archives_recorder.sql 2947 2012-05-24 14:04:43Z ggolden $
--**********************************************************************************
--
-- Copyright (c) 2009, 2010, 2011, 2012 Etudes, Inc.
-- 
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
-- 
--      http://www.apache.org/licenses/LICENSE-2.0
-- 
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--
--*********************************************************************************/

-----------------------------------------------------------------------------
-- Archives Recorder DDL
-----------------------------------------------------------------------------

CREATE TABLE ARCHIVES_SITES_ARCHIVED
(
  ID BIGINT UNSIGNED AUTO_INCREMENT NOT NULL PRIMARY KEY,
  SITE_ID VARCHAR (99) NOT NULL,
  DATE_ARCHIVED BIGINT NOT NULL,
  TITLE VARCHAR (99) NULL,
  TERM_ID BIGINT UNSIGNED NOT NULL,
  UNIQUE KEY ARCHIVES_SITES_ARCHIVED_IDX_S (SITE_ID),
  KEY ARCHIVES_SITES_ARCHIVED_IDX_T (TERM_ID)
);

CREATE TABLE ARCHIVES_SITES_PURGED
(
  ID BIGINT UNSIGNED AUTO_INCREMENT NOT NULL PRIMARY KEY,
  SITE_ID VARCHAR (99) NOT NULL,
  DATE_PURGED BIGINT NOT NULL,
  TITLE VARCHAR (99) NULL,
  TERM_ID BIGINT UNSIGNED NOT NULL,
  UNIQUE KEY ARCHIVES_SITES_PURGED_IDX_S (SITE_ID),
  KEY ARCHIVES_SITES_PURGED_IDX_T (TERM_ID)
);

CREATE TABLE ARCHIVES_OWNERS
(
  ID BIGINT UNSIGNED AUTO_INCREMENT NOT NULL PRIMARY KEY,
  ARCHIVES_ID BIGINT UNSIGNED NOT NULL,
  USER_ID VARCHAR (99) NOT NULL,
  KEY ARCHIVES_OWNERS_IDX_U (USER_ID),
  KEY ARCHIVES_OWNERS_IDX_A (ARCHIVES_ID)
);
