/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/PurgeSiteHandler.java $
 * $Id: PurgeSiteHandler.java 5165 2013-06-11 19:37:56Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2009, 2010, 2011, 2012, 2013 Etudes, Inc.
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
 * Archives PurgeHandler for Site based on direct database manipulation
 */
public class PurgeSiteHandler implements PurgeHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(PurgeSiteHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.site";

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

		delete("DELETE FROM SAKAI_SITE_TOOL_PROPERTY WHERE SITE_ID=?", fields);
		delete("DELETE FROM SAKAI_SITE_TOOL WHERE SITE_ID=?", fields);
		delete("DELETE FROM SAKAI_SITE_PAGE_PROPERTY WHERE SITE_ID=?", fields);
		delete("DELETE FROM SAKAI_SITE_PAGE WHERE SITE_ID=?", fields);
		delete("DELETE FROM SAKAI_SITE_USER WHERE SITE_ID=?", fields);
		delete("DELETE FROM SAKAI_SITE_GROUP_PROPERTY WHERE SITE_ID=?", fields);
		delete("DELETE FROM SAKAI_SITE_GROUP WHERE SITE_ID=?", fields);
		delete("DELETE FROM SAKAI_SITE_PROPERTY WHERE SITE_ID=?", fields);
		delete("DELETE FROM SAKAI_USER_SITE_ACCESS WHERE SITE_ID=?", fields);

		// for now, lets leave the site_term record in place, in case we need to find the sites in the term again
		// delete("DELETE FROM SITE_TERM WHERE SITE_ID=?", fields);

		// some related other things ...
		delete("DELETE FROM AM_SITE_VISIT WHERE CONTEXT=?", fields);

		// Note: no index on context, but the table should be pretty small
		// also: context is not site id, but...
		delete("DELETE FROM ET_SCH_DELAYED_INVOCATION WHERE CONTEXT=?", fields);
		fields[0] = "/announcement/msg/" + siteId + "/%";
		delete("DELETE FROM ET_SCH_DELAYED_INVOCATION WHERE CONTEXT LIKE ?", fields);
		fields[0] = siteId;

		delete("DELETE FROM SAKAI_SITE WHERE SITE_ID=?", fields);
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
