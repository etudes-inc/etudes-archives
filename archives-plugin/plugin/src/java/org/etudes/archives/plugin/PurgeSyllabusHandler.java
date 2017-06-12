/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/PurgeSyllabusHandler.java $
 * $Id: PurgeSyllabusHandler.java 2845 2012-04-16 20:19:00Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2009, 2010, 2011, 2012 Etudes, Inc.
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.PurgeAttachmentHandler;
import org.etudes.archives.api.PurgeHandler;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.util.StringUtil;

/**
 * Archives PurgeHandler for Syllabus
 */
public class PurgeSyllabusHandler implements PurgeHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(PurgeSyllabusHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.syllabus";

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

		// get any attachment CHS references that are not in the site's attachments area
		List<String> attachments = readAttachmentInfo(siteId);

		// for the prepared statements
		Object[] fields = new Object[1];
		fields[0] = siteId;

		delete("DELETE SAKAI_SYLLABUS_ATTACH FROM SAKAI_SYLLABUS_ATTACH"
				+ " INNER JOIN SAKAI_SYLLABUS_DATA ON SAKAI_SYLLABUS_ATTACH.SYLLABUSID = SAKAI_SYLLABUS_DATA.ID"
				+ " INNER JOIN SAKAI_SYLLABUS_ITEM ON SAKAI_SYLLABUS_DATA.SURROGATEKEY = SAKAI_SYLLABUS_ITEM.ID"
				+ " WHERE SAKAI_SYLLABUS_ITEM.CONTEXTID = ?", fields);

		delete("DELETE SAKAI_SYLLABUS_DATA FROM SAKAI_SYLLABUS_DATA"
				+ " INNER JOIN SAKAI_SYLLABUS_ITEM ON SAKAI_SYLLABUS_DATA.SURROGATEKEY = SAKAI_SYLLABUS_ITEM.ID"
				+ " WHERE SAKAI_SYLLABUS_ITEM.CONTEXTID = ?", fields);

		delete("DELETE FROM SAKAI_SYLLABUS_ITEM WHERE SAKAI_SYLLABUS_ITEM.CONTEXTID = ?", fields);

		delete("DELETE FROM SAKAI_SYLLABUS_TRACK_VIEW WHERE CONTEXT_ID = ?", fields);

		// purge the attachments that are outside the normal attachment areas for the site
		PurgeAttachmentHandler handler = this.archivesService.getPurgeAttachmentHandler();
		if (handler != null)
		{
			for (String id : attachments)
			{
				handler.purgeAttachment(id, true);
			}
		}
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

	/**
	 * Find the content hosting attachment references which are NOT part of the site
	 * 
	 * @param siteId
	 *        The context site Id
	 * @return List<String> for each attachment not in the site found.
	 */
	protected List<String> readAttachmentInfo(final String siteId)
	{
		String sql = "SELECT ATTACHMENTID FROM SAKAI_SYLLABUS_ATTACH"
				+ " INNER JOIN SAKAI_SYLLABUS_DATA ON SAKAI_SYLLABUS_ATTACH.SYLLABUSID = SAKAI_SYLLABUS_DATA.ID"
				+ " INNER JOIN SAKAI_SYLLABUS_ITEM ON SAKAI_SYLLABUS_DATA.SURROGATEKEY = SAKAI_SYLLABUS_ITEM.ID"
				+ " WHERE SAKAI_SYLLABUS_ITEM.CONTEXTID = ?";
		Object[] fields = new Object[1];
		fields[0] = siteId;

		List<String> rv = this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					String id = StringUtil.trimToNull(result.getString(1));
					// /attachment/<site>/Syllabus/21c64202-b906-4c81-8029-0ac4f37a0f29/http:__www.etudes.org
					String siteReferenced = id.substring("/attachment/".length(), id.indexOf("/Syllabus"));
					if (!siteReferenced.equals(siteId))
					{
						return "/content" + id;
					}

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("readAttachmentInfo: " + e);
					return null;
				}
				catch (IndexOutOfBoundsException e)
				{
					return null;
				}
			}
		});

		return rv;
	}
}
