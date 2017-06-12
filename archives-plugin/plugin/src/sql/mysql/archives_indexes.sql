--*********************************************************************************
-- $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/sql/mysql/archives_indexes.sql $
-- $Id: archives_indexes.sql 2823 2012-04-03 20:57:39Z ggolden $
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
-- Extra indexes needed for Archives
-----------------------------------------------------------------------------


CREATE INDEX BLOGGER_POST_IDX_S ON BLOGGER_POST (site_id ASC);
CREATE INDEX BLOGGER_IMAGE_IDX_P ON BLOGGER_IMAGE (post_id ASC);
CREATE INDEX BLOGGER_FILE_IDX_P ON BLOGGER_FILE (post_id ASC);

CREATE INDEX CONTENT_RESOURCE_DELETE_IDX_IC ON CONTENT_RESOURCE_DELETE (IN_COLLECTION ASC);

CREATE INDEX rwikicurrentcontent_idx_r ON rwikicurrentcontent (rwikiid ASC);
CREATE INDEX rwikihistory_idx_r ON rwikihistory (realm ASC);
CREATE INDEX rwikihistorycontent_idx_r ON rwikihistorycontent (rwikiid ASC);
CREATE INDEX rwikiobject_idx_r ON rwikiobject (realm ASC);
CREATE INDEX rwikipagemessage_idx_p ON rwikipagemessage (pagespace ASC);
CREATE INDEX rwikipagepresence_idx_p ON rwikipagepresence (pagespace ASC);
CREATE INDEX rwikipagetrigger_idx_p ON rwikipagetrigger (pagespace ASC);
CREATE INDEX rwikipreference_idx_p ON rwikipreference (prefcontext ASC);

CREATE INDEX MNEME_QUESTION_IDX_C ON MNEME_QUESTION (CONTEXT ASC);

-- this may exist
CREATE INDEX COURSE_ID_IDX ON melete_course_module (course_id ASC);

CREATE INDEX SAKAI_SYLLABUS_ITEM_IDX_CID ON SAKAI_SYLLABUS_ITEM (CONTEXTID ASC);

CREATE INDEX jforum_privmsgs_attach_IDX_P ON jforum_privmsgs_attach (privmsgs_id ASC);
