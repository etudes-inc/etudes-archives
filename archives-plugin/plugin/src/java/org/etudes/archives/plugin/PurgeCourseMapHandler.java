/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/PurgeCourseMapHandler.java $
 * $Id: PurgeCourseMapHandler.java 2863 2012-04-24 18:29:42Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2012 Etudes, Inc.
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
 * Archives PurgeHandler for CourseMap
 */
public class PurgeCourseMapHandler implements PurgeHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(PurgeCourseMapHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.coursemap";

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

		Object[] fields = new Object[1];
		fields[0] = siteId;

		delete("DELETE FROM COURSEMAP_MAP WHERE CONTEXT = ?", fields);
		delete("DELETE FROM COURSEMAP_HEADER WHERE CONTEXT = ?", fields);
		delete("DELETE FROM COURSEMAP_ITEM WHERE CONTEXT = ?", fields);
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
