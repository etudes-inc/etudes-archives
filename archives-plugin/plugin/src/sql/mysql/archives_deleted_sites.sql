--*********************************************************************************
-- $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/sql/mysql/archives_deleted_sites.sql $
-- $Id: archives_deleted_sites.sql 3070 2012-07-09 17:56:53Z ggolden $
--**********************************************************************************
--
-- Copyright (c) 2009 Etudes, Inc.
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
-- Find the deleted sites from the records left behind
-- (term_id 2 is assumed to be 'deleted')
-----------------------------------------------------------------------------

INSERT INTO ARCHIVES_SITE_TERM (SITE_ID, TERM_ID)
SELECT X.SITE_ID, X.TERM_ID FROM
(
SELECT SUBSTRING_INDEX(SUBSTRING(C.CHANNEL_ID,15),'/',1) AS SITE_ID, 2 AS TERM_ID FROM CHAT_CHANNEL C
LEFT OUTER JOIN SAKAI_SITE S ON SUBSTRING_INDEX(SUBSTRING(C.CHANNEL_ID,15),'/',1) = S.SITE_ID
WHERE S.SITE_ID IS NULL
UNION
SELECT SUBSTRING_INDEX(SUBSTRING(C.CHANNEL_ID,23),'/',1) AS SITE_ID, 2 AS TERM_ID FROM ANNOUNCEMENT_CHANNEL C
LEFT OUTER JOIN SAKAI_SITE S ON SUBSTRING_INDEX(SUBSTRING(C.CHANNEL_ID,23),'/',1) = S.SITE_ID
WHERE S.SITE_ID IS NULL
UNION
SELECT SUBSTRING_INDEX(SUBSTRING(C.COLLECTION_ID,8),'/',1) AS SITE_ID, 2 AS TERM_ID FROM CONTENT_COLLECTION C
LEFT OUTER JOIN SAKAI_SITE S ON SUBSTRING_INDEX(SUBSTRING(C.COLLECTION_ID,8),'/',1) = S.SITE_ID
WHERE C.COLLECTION_ID LIKE '/group/%'
AND C.COLLECTION_ID = CONCAT(SUBSTRING_INDEX(C.COLLECTION_ID,'/',3),'/')
AND S.SITE_ID IS NULL
UNION
SELECT SUBSTRING_INDEX(SUBSTRING(C.CALENDAR_ID,20),'/',1) AS SITE_ID, 2 AS TERM_ID FROM CALENDAR_CALENDAR C
LEFT OUTER JOIN SAKAI_SITE S ON SUBSTRING_INDEX(SUBSTRING(C. CALENDAR_ID,20),'/',1) = S.SITE_ID
WHERE S.SITE_ID IS NULL
UNION
SELECT COURSE_ID AS SITE_ID, 2 AS TERM_ID FROM jforum_sakai_course_categories C
LEFT OUTER JOIN SAKAI_SITE S ON C.COURSE_ID=S.SITE_ID
WHERE S.SITE_ID IS NULL
UNION
SELECT COURSE_ID AS SITE_ID, 2 AS TERM_ID FROM melete_course_module C
LEFT OUTER JOIN SAKAI_SITE S ON C.COURSE_ID=S.SITE_ID
WHERE S.SITE_ID IS NULL
UNION
SELECT CONTEXT AS SITE_ID, 2 AS TERM_ID FROM MNEME_POOL C
LEFT OUTER JOIN SAKAI_SITE S ON C.CONTEXT=S.SITE_ID
WHERE S.SITE_ID IS NULL
UNION
SELECT CONTEXT AS SITE_ID, 2 AS TERM_ID FROM MNEME_ASSESSMENT C
LEFT OUTER JOIN SAKAI_SITE S ON C.CONTEXT=S.SITE_ID
WHERE S.SITE_ID IS NULL
UNION
SELECT SUBSTRING_INDEX(SUBSTRING(C.COLLECTION_ID,13),'/',1) AS SITE_ID, 2 AS TERM_ID FROM CONTENT_COLLECTION C
LEFT OUTER JOIN SAKAI_SITE S ON SUBSTRING_INDEX(SUBSTRING(C.COLLECTION_ID,13),'/',1) = S.SITE_ID
WHERE C.COLLECTION_ID LIKE '/attachment/%'
AND C.COLLECTION_ID = CONCAT(SUBSTRING_INDEX(C.COLLECTION_ID,'/',3),'/')
AND S.SITE_ID IS NULL
) X
LEFT OUTER JOIN ARCHIVES_SITE_TERM T ON X.SITE_ID=T.SITE_ID
WHERE T.SITE_ID IS NULL
