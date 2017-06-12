/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/PurgeUserJforumHandler.java $
 * $Id: PurgeUserJforumHandler.java 3023 2012-06-20 18:21:58Z ggolden $
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

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.PurgeUserHandler;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.util.StringUtil;

/**
 * Archives PurgeUserHandler for JForum user data
 */
public class PurgeUserJforumHandler implements PurgeUserHandler
{
	public class JforumUserInfo
	{
		String avatar;
		Integer id;
	}

	/** Our log. */
	private static Log M_log = LogFactory.getLog(PurgeUserJforumHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.jforum";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Configuration: The file system root to the avatar files. */
	protected String fileRoot = null;

	/** Dependency: ServerConfigurationService */
	protected ServerConfigurationService serverConfigurationService = null;

	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterPurgeUserHandler(applicationId, this);
		M_log.info("destroy()");
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		this.archivesService.registerPurgeUserHandler(applicationId, this);

		// check for a configuration for the JForum avatar file root
		String root = this.serverConfigurationService.getString("etudes.jforum.avatar.path");
		if (root != null)
		{
			if (!root.endsWith("/")) root = root + "/";
			this.fileRoot = root;

			this.fileRoot += "images/avatar/";
		}

		M_log.info("init()");
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public void purge(String userId)
	{
		M_log.info("purge user " + applicationId + " for user: " + userId);

		// read the jforum user id
		Object[] fields = new Object[1];
		fields[0] = userId;
		String sql = "SELECT USER_ID, USER_AVATAR FROM JFORUM_USERS WHERE SAKAI_USER_ID = ?";
		List<JforumUserInfo> results = this.sqlService.dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					JforumUserInfo rv = new JforumUserInfo();
					rv.id = Integer.parseInt(StringUtil.trimToNull(result.getString(1)));
					rv.avatar = StringUtil.trimToNull(result.getString(2));
					return rv;
				}
				catch (SQLException e)
				{
					M_log.warn("readSyllabusData: " + e.toString());
					return null;
				}
				catch (IndexOutOfBoundsException e)
				{
					return null;
				}
			}
		});

		// if not found, no jforum user information to worry about
		if ((results == null) || (results.isEmpty())) return;

		// switch the fill-in value to the jforum user id number
		fields[0] = results.get(0).id;

		delete("DELETE FROM JFORUM_SEARCH_TOPICS WHERE USER_ID = ?", fields);

		// TODO: JFORUM_SESSIONS may not be used
		delete("DELETE FROM JFORUM_SESSIONS WHERE SESSION_USER_ID = ?", fields);

		delete("DELETE FROM JFORUM_USERS WHERE USER_ID = ?", fields);

		// delete the avatar
		if ((results.get(0).avatar != null) && (this.fileRoot != null))
		{
			String path = this.fileRoot + results.get(0).avatar;
			File file = new File(path);
			if (file.exists())
			{
				file.delete();
			}
			else
			{
				M_log.warn("purge: missing avatar file: " + path);
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
	 * Dependency: ServerConfigurationService.
	 * 
	 * @param service
	 *        The ServerConfigurationService.
	 */
	public void setServerConfigurationService(ServerConfigurationService service)
	{
		this.serverConfigurationService = service;
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
