/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/PurgeAnnouncementsHandler.java $
 * $Id: PurgeAnnouncementsHandler.java 2823 2012-04-03 20:57:39Z ggolden $
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.PurgeAttachmentHandler;
import org.etudes.archives.api.PurgeHandler;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.util.StringUtil;

/**
 * Archives PurgeHandler for Announcements
 */
public class PurgeAnnouncementsHandler implements PurgeHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(PurgeAnnouncementsHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.announcements";

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

		// site's channel is /announcement/channel/<site id>/main
		String channel = "/announcement/channel/" + siteId + "/main";

		// get any attachment CHS references that are not in the site's attachments area
		List<String> attachments = readAttachmentInfo(siteId, channel);

		// channel id for the prepared statements
		Object[] fields = new Object[1];
		fields[0] = channel;

		delete("DELETE FROM ANNOUNCEMENT_MESSAGE WHERE CHANNEL_ID = ?", fields);
		delete("DELETE FROM ANNOUNCEMENT_CHANNEL WHERE CHANNEL_ID = ?", fields);

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
	 * @param channel
	 *        The channel id.
	 * @return List<String> for each attachment not in the site found.
	 */
	protected List<String> readAttachmentInfo(final String siteId, String channel)
	{
		String sql = "SELECT XML FROM ANNOUNCEMENT_MESSAGE WHERE CHANNEL_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = channel;

		final List<String> rv = new ArrayList<String>();
		this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					String xml = StringUtil.trimToNull(result.getString(1));

					// find the attachment(s) in the xml
					// <attachment relative-url="/content/attachment/~92062...c63/www.etudes.org"/>
					Pattern p = Pattern.compile("attachment relative-url=\"([^\"]*)\"");
					Matcher m = p.matcher(xml);
					while (m.find())
					{
						if (m.groupCount() == 1)
						{
							String id = m.group(1);
							String siteReferenced = id.substring("/content/attachment/".length(), id.indexOf("/", "/content/attachment/".length()));
							if (!siteReferenced.equals(siteId))
							{
								rv.add(id);
							}
						}
					}
				}
				catch (SQLException e)
				{
					M_log.warn("readAttachmentInfo: " + e);
				}
				catch (IndexOutOfBoundsException e)
				{
				}
				return null;
			}
		});

		return rv;
	}
}
