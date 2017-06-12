/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/PurgeAuthzHandler.java $
 * $Id: PurgeAuthzHandler.java 2823 2012-04-03 20:57:39Z ggolden $
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.PurgeHandler;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.util.StringUtil;

/**
 * Archives PurgeHandler for Authz
 */
public class PurgeAuthzHandler implements PurgeHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(PurgeAuthzHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.authz";

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

		// for the site
		Integer key = readRealmKey("/site/" + siteId);
		if (key != null) purgeRealm(key);

		// for any site groups
		List<Integer> keys = readRealmKeys("/site/" + siteId + "/group/");
		for (Integer i : keys)
		{
			purgeRealm(i);
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
	 * Purge the realm with this key.
	 * 
	 * @param key
	 *        The realm key.
	 */
	protected void purgeRealm(Integer key)
	{
		// realm key for the prepared statements
		Object[] fields = new Object[1];
		fields[0] = key;

		delete("DELETE FROM SAKAI_REALM_RL_FN WHERE REALM_KEY = ?", fields);
		delete("DELETE FROM SAKAI_REALM_RL_GR WHERE REALM_KEY = ?", fields);
		delete("DELETE FROM SAKAI_REALM_PROVIDER WHERE REALM_KEY = ?", fields);
		delete("DELETE FROM SAKAI_REALM_ROLE_DESC WHERE REALM_KEY = ?", fields);
		delete("DELETE FROM SAKAI_REALM_PROPERTY WHERE REALM_KEY = ?", fields);
		delete("DELETE FROM SAKAI_REALM WHERE REALM_KEY = ?", fields);
	}

	/**
	 * Lookup the realm key for the realm id.
	 * 
	 * @param realmId
	 *        The realmId
	 * @return The realm key (Integer) or null if not found.
	 */
	protected Integer readRealmKey(String realmId)
	{
		String sql = "SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = realmId;

		List<Integer> rv = this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					String str = StringUtil.trimToNull(result.getString(1));
					if (str == null) return null;
					try
					{
						Integer key = Integer.valueOf(str);
						return key;
					}
					catch (NumberFormatException e)
					{
					}

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("readRealmKey: " + e);
					return null;
				}
			}
		});

		if ((rv == null) || (rv.isEmpty())) return null;

		return rv.get(0);
	}

	/**
	 * Lookup the realm keys for the realms that match this prefix.
	 * 
	 * @param realmPrefix
	 *        The realm id prefix.
	 * @return The list of realm keys (Integer) or an empty list if none found.
	 */
	protected List<Integer> readRealmKeys(String realmPrefix)
	{
		String sql = "SELECT REALM_KEY FROM SAKAI_REALM WHERE REALM_ID LIKE ?";
		Object[] fields = new Object[1];
		fields[0] = realmPrefix + "%";

		List<Integer> rv = this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					String str = StringUtil.trimToNull(result.getString(1));
					if (str == null) return null;
					try
					{
						Integer key = Integer.valueOf(str);
						return key;
					}
					catch (NumberFormatException e)
					{
					}

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("readRealmKeys: " + e);
					return null;
				}
			}
		});

		if ((rv == null) || (rv.isEmpty())) return new ArrayList<Integer>();

		return rv;
	}
}
