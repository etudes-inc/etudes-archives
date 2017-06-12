/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/PurgeGradebookHandler.java $
 * $Id: PurgeGradebookHandler.java 2916 2012-05-08 21:39:58Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2009 Etudes, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.etudes.archives.plugin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.PurgeHandler;
import org.sakaiproject.db.api.SqlService;

/**
 * Archives PurgeHandler for Gradebook
 */
public class PurgeGradebookHandler implements PurgeHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(PurgeGradebookHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.gradebook.tool";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterPurgeHandler(applicationId, this);
		M_log.info("destroy()");
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		this.archivesService.registerPurgeHandler(applicationId, this);
		M_log.info("init()");
	}

	/**
	 * {@inheritDoc}
	 */
	public void purge(String siteId)
	{
		M_log.info("purge " + applicationId + " in site: " + siteId);

		// site id for the prepared statements
		Object[] fields = new Object[1];
		fields[0] = siteId;

		delete("DELETE GB_GRADING_EVENT_T FROM GB_GRADING_EVENT_T "
				+ " INNER JOIN GB_GRADABLE_OBJECT_T ON GB_GRADING_EVENT_T.GRADABLE_OBJECT_ID = GB_GRADABLE_OBJECT_T.ID"
				+ " INNER JOIN GB_GRADEBOOK_T ON GB_GRADABLE_OBJECT_T.GRADEBOOK_ID = GB_GRADEBOOK_T.ID" + " WHERE GB_GRADEBOOK_T.GRADEBOOK_UID = ?",
				fields);
		delete("DELETE GB_GRADE_RECORD_T FROM GB_GRADE_RECORD_T "
				+ " INNER JOIN GB_GRADABLE_OBJECT_T ON GB_GRADE_RECORD_T.GRADABLE_OBJECT_ID = GB_GRADABLE_OBJECT_T.ID"
				+ " INNER JOIN GB_GRADEBOOK_T ON GB_GRADABLE_OBJECT_T.GRADEBOOK_ID = GB_GRADEBOOK_T.ID" + " WHERE GB_GRADEBOOK_T.GRADEBOOK_UID = ?",
				fields);
		delete("DELETE GB_GRADABLE_OBJECT_T FROM GB_GRADABLE_OBJECT_T "
				+ " INNER JOIN GB_GRADEBOOK_T ON GB_GRADABLE_OBJECT_T.GRADEBOOK_ID = GB_GRADEBOOK_T.ID                       "
				+ " WHERE GB_GRADEBOOK_T.GRADEBOOK_UID = ?", fields);
		delete("DELETE GB_SPREADSHEET_T FROM GB_SPREADSHEET_T                                                                "
				+ " INNER JOIN GB_GRADEBOOK_T ON GB_SPREADSHEET_T.GRADEBOOK_ID = GB_GRADEBOOK_T.ID                           "
				+ " WHERE GB_GRADEBOOK_T.GRADEBOOK_UID = ?", fields);
		delete("DELETE GB_GRADE_TO_PERCENT_MAPPING_T FROM GB_GRADE_TO_PERCENT_MAPPING_T "
				+ " INNER JOIN GB_GRADE_MAP_T ON GB_GRADE_TO_PERCENT_MAPPING_T.GRADE_MAP_ID = GB_GRADE_MAP_T.ID"
				+ " INNER JOIN GB_GRADEBOOK_T ON GB_GRADE_MAP_T.GRADEBOOK_ID = GB_GRADEBOOK_T.ID                             "
				+ " WHERE GB_GRADEBOOK_T.GRADEBOOK_UID = ?", fields);

		// clear the back pointer (foreign key constraint) so we can delete the records from GB_GRADE_MAP_T
		delete("UPDATE GB_GRADEBOOK_T SET SELECTED_GRADE_MAPPING_ID = NULL WHERE GRADEBOOK_UID = ?", fields);

		delete("DELETE GB_GRADE_MAP_T FROM GB_GRADE_MAP_T " + " INNER JOIN GB_GRADEBOOK_T ON GB_GRADE_MAP_T.GRADEBOOK_ID = GB_GRADEBOOK_T.ID"
				+ " WHERE GB_GRADEBOOK_T.GRADEBOOK_UID = ?", fields);
		delete("DELETE FROM GB_GRADEBOOK_T WHERE GB_GRADEBOOK_T.GRADEBOOK_UID = ?", fields);
	}

	/**
	 * Set the archives service.
	 * 
	 * @param service
	 *        The archives service.
	 */
	public void setArchivesService(ArchivesService service)
	{
		this.archivesService = service;
	}

	/**
	 * {@inheritDoc}
	 */
	public void setSqlService(SqlService service)
	{
		this.sqlService = service;
	}

	/**
	 * Do a delete.
	 * 
	 * @param query
	 *        The delete query.
	 * @param fields
	 *        the prepared statement fields.
	 */
	protected void delete(final String query, final Object[] fields)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				deleteTx(query, fields);
			}
		}, "delete: " + fields[0] + " " + query);
	}

	/**
	 * Do a delete (transaction code)
	 * 
	 * @param query
	 *        The delete query.
	 * @param fields
	 *        the prepared statement fields.
	 */
	protected void deleteTx(String query, Object[] fields)
	{
		if (!this.sqlService.dbWrite(query, fields))
		{
			throw new RuntimeException("deleteTx: db write failed: " + fields[0] + " " + query);
		}
	}
}
