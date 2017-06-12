/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/PurgeChsHandler.java $
 * $Id: PurgeChsHandler.java 2823 2012-04-03 20:57:39Z ggolden $
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.PurgeAttachmentHandler;
import org.etudes.archives.api.PurgeHandler;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.util.StringUtil;

/**
 * Archives PurgeHandler for Content Hosting. Note the "content_resource_lock" table name is created in lower case.
 */
public class PurgeChsHandler implements PurgeHandler, PurgeAttachmentHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(PurgeChsHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.resources";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** The root to the content hosting resource files, if they are external to the database. */
	protected String fileRoot = null;

	/** Dependency: ServerConfigurationService. */
	protected ServerConfigurationService serverConfigurationService = null;

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

		// find the content hosting body path
		this.fileRoot = StringUtil.trimToNull(this.serverConfigurationService
				.getString("bodyPath@org.sakaiproject.content.api.ContentHostingService"));

		M_log.info("init(): file root: " + this.fileRoot);
	}

	/**
	 * {@inheritDoc}
	 */
	public void purge(String siteId)
	{
		M_log.info("purge " + applicationId + " in site: " + siteId);

		// user site?
		if (siteId.startsWith("~"))
		{
			purgeUserSite(siteId);
		}
		else
		{
			purgeSite(siteId);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void purgeAttachment(String resourceRef, boolean deleteEmptyCollection)
	{
		// remove the reference prefix
		String id = resourceRef.substring("/content".length());

		// get the in_collection if we need it
		String inCollection = null;
		if (deleteEmptyCollection)
		{
			inCollection = readResourceInCollection(id);
		}

		// delete the resource
		deleteResource(id);

		if (deleteEmptyCollection && (inCollection != null))
		{
			// see if the resource's collection is empty
			List<String> collections = readCollectionCollections(inCollection);
			List<String> resources = readCollectionResources(inCollection);
			if (collections.isEmpty() && resources.isEmpty())
			{
				deleteCollection(inCollection);
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
	 * Set the ServerConfigurationService.
	 * 
	 * @param service
	 *        the ServerConfigurationService.
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
	 * Fully delete the authz for this content hosting id
	 * 
	 * @param id
	 *        The content hosting id
	 */
	protected void deleteAuthz(String id)
	{
		String realmId = "/content" + id;
		Integer realmKey = readRealmKey(realmId);
		if (realmKey == null) return;

		Object[] fields = new Object[1];
		fields[0] = realmKey;

		delete("DELETE FROM SAKAI_REALM_RL_FN WHERE REALM_KEY = ?", fields);
		delete("DELETE FROM SAKAI_REALM_RL_GR WHERE REALM_KEY = ?", fields);
		delete("DELETE FROM SAKAI_REALM_PROVIDER WHERE REALM_KEY = ?", fields);
		delete("DELETE FROM SAKAI_REALM_ROLE_DESC WHERE REALM_KEY = ?", fields);
		delete("DELETE FROM SAKAI_REALM_PROPERTY WHERE REALM_KEY = ?", fields);
		delete("DELETE FROM SAKAI_REALM WHERE REALM_KEY = ?", fields);
	}

	/**
	 * Fully delete the collection with this id.
	 * 
	 * @param id
	 *        The collection id.
	 */
	protected void deleteCollection(String id)
	{
		deleteAuthz(id);

		Object[] fields = new Object[1];
		fields[0] = id;

		delete("DELETE FROM CONTENT_COLLECTION WHERE COLLECTION_ID = ?", fields);

		// delete all the deleted resource records for the collection
		delete("DELETE FROM CONTENT_RESOURCE_DELETE WHERE IN_COLLECTION = ?", fields);

	}

	/**
	 * Fully delete the resource with this id.
	 * 
	 * @param id
	 *        The resource id.
	 */
	protected void deleteResource(String id)
	{
		deleteAuthz(id);

		Object[] fields = new Object[1];
		fields[0] = id;

		deleteResourceFile(id);

		delete("DELETE FROM CONTENT_RESOURCE_BODY_BINARY WHERE RESOURCE_ID = ?", fields);
		delete("DELETE FROM CONTENT_RESOURCE WHERE RESOURCE_ID = ?", fields);
	}

	/**
	 * Delete the external file for the resource
	 * 
	 * @param id
	 *        The content hosting resource id.
	 */
	protected void deleteResourceFile(String id)
	{
		// skip if the resources are internal
		if (this.fileRoot == null) return;

		String filePath = readResourceFilePath(id);
		if (filePath == null) return;

		// the external file
		String name = this.fileRoot + filePath;
		File file = new File(name);

		try
		{
			// delete
			if (file.exists())
			{
				File container = file.getParentFile();

				file.delete();

				// walk back clearing empty directories
				String root = new File(this.fileRoot).getCanonicalPath();
				while (true)
				{
					// don't delete the root path
					if (container.getCanonicalPath().equals(root)) break;

					File toDelete = container;
					container = toDelete.getParentFile();

					if (!toDelete.delete()) break;
				}
			}
		}
		catch (IOException e)
		{
			M_log.warn("deleteResourceFile: " + e);
		}
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
	 * Delete the collection and all contained collections and resources
	 * 
	 * @param id
	 *        The collection id.
	 */
	protected void purgeCollection(String id)
	{
		// find the collection children
		List<String> collections = readCollectionCollections(id);

		// purge them
		for (String collection : collections)
		{
			purgeCollection(collection);
		}

		// find the resource children
		List<String> resources = readCollectionResources(id);

		// purge them
		for (String resource : resources)
		{
			deleteResource(resource);
		}

		// remove the collection
		deleteCollection(id);
	}

	/**
	 * Purge this non-user site.
	 * 
	 * @param siteId
	 *        The site id.
	 */
	protected void purgeSite(String siteId)
	{
		// purge the main group
		purgeCollection("/group/" + siteId + "/");

		// purge the drop-boxes
		purgeCollection("/group-user/" + siteId + "/");

		// purge attachments
		purgeCollection("/attachment/" + siteId + "/");

		// purge private areas for Mneme, Melete
		purgeCollection("/private/mneme/" + siteId + "/");
		purgeCollection("/private/meleteDocs/" + siteId + "/");
	}

	/**
	 * Purge this user site.
	 * 
	 * @param siteId
	 *        The site id.
	 */
	protected void purgeUserSite(String siteId)
	{
		// the site id without the leading "~"
		String modifiedSiteId = siteId.substring(1);

		// purge the main user area
		purgeCollection("/user/" + modifiedSiteId + "/");

		// purge attachments (using the original site id with the "~")
		purgeCollection("/attachment/" + siteId + "/");
	}

	/**
	 * {@inheritDoc}
	 */
	protected String readAttachment(String resourceRef)
	{
		InputStream in = streamAttachment(resourceRef);
		if (in == null) return null;

		StringBuilder rv = new StringBuilder();

		BufferedReader buf = null;
		try
		{
			buf = new BufferedReader(new InputStreamReader(in, "UTF-8"));
			while (true)
			{
				String str = buf.readLine();
				if (str == null) break;

				rv.append(str);
				rv.append("\n");
			}
		}
		catch (IOException e)
		{
			M_log.warn("readAttachment: " + e);
		}
		finally
		{
			try
			{
				if (buf != null) buf.close();
			}
			catch (IOException e)
			{
				M_log.warn("readAttachment: closing buf: " + e);
			}

			try
			{
				if (in != null) in.close();
			}
			catch (IOException e)
			{
				M_log.warn("readAttachment: closing in: " + e);
			}
		}

		return rv.toString();
	}

	/**
	 * Read the collection ids that are directly held in the collection
	 * 
	 * @param id
	 *        The collection id
	 * @return The List<String> of collection ids.
	 */
	protected List<String> readCollectionCollections(String id)
	{
		String sql = "SELECT COLLECTION_ID FROM CONTENT_COLLECTION WHERE IN_COLLECTION = ?";
		Object[] fields = new Object[1];
		fields[0] = id;

		List<String> rv = this.sqlService.dbRead(sql.toString(), fields, null);

		return rv;
	}

	/**
	 * Read the resource ids that are directly held in the collection
	 * 
	 * @param id
	 *        The collection id
	 * @return The List<String> of resource ids.
	 */
	protected List<String> readCollectionResources(String id)
	{
		String sql = "SELECT RESOURCE_ID FROM CONTENT_RESOURCE WHERE IN_COLLECTION = ?";
		Object[] fields = new Object[1];
		fields[0] = id;

		List<String> rv = this.sqlService.dbRead(sql.toString(), fields, null);

		return rv;
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
	 * Get a stream on the resource body from the db.
	 * 
	 * @param id
	 *        The resource id.
	 * @return The InputStream on the resource body from the db - make sure to close it!
	 */
	protected InputStream readResourceBodyDb(String id)
	{
		String sql = "SELECT BODY FROM CONTENT_RESOURCE_BODY_BINARY WHERE RESOURCE_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = id;

		try
		{
			// get the stream, set expectations that this could be big
			InputStream in = this.sqlService.dbReadBinary(sql, fields, true);
			return in;
		}
		catch (ServerOverloadException e)
		{
			M_log.warn("readResourceBodyDb: " + e);
			return null;
		}
	}

	/**
	 * Get a stream on the resource body from the file system.
	 * 
	 * @param filePath
	 *        The resource's file's path on the file system (relative to the root).
	 * @return The InputStream on the resource body from the file system - make sure to close it!
	 */
	protected InputStream readResourceBodyFs(String filePath)
	{
		// form the file name
		String name = this.fileRoot + filePath;
		File file = new File(name);

		// read the new
		try
		{
			FileInputStream in = new FileInputStream(file);
			return in;
		}
		catch (FileNotFoundException e)
		{
			M_log.warn("readResourceBodyDb: " + e);
			return null;
		}
	}

	/**
	 * Read the file_path value for this resource
	 * 
	 * @param id
	 *        The resource id
	 * @return The file_path value, or null if not set.
	 */
	protected String readResourceFilePath(String id)
	{
		String sql = "SELECT FILE_PATH FROM CONTENT_RESOURCE WHERE RESOURCE_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = id;

		List<String> rv = this.sqlService.dbRead(sql.toString(), fields, null);

		if ((rv == null) || (rv.isEmpty())) return null;

		return rv.get(0);
	}

	/**
	 * Read the in_collection value for this resource
	 * 
	 * @param id
	 *        The resource id
	 * @return The in_collection value, or null if not set.
	 */
	protected String readResourceInCollection(String id)
	{
		String sql = "SELECT IN_COLLECTION FROM CONTENT_RESOURCE WHERE RESOURCE_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = id;

		List<String> rv = this.sqlService.dbRead(sql.toString(), fields, null);

		if ((rv == null) || (rv.isEmpty())) return null;

		return rv.get(0);
	}

	/**
	 * Get a stream on the attachment, from the file system or database
	 * 
	 * @param resourceRef
	 *        The resource reference
	 * @return The input stream, or null if not found.
	 */
	protected InputStream streamAttachment(String resourceRef)
	{
		// remove the reference prefix
		String id = resourceRef.substring("/content".length());

		String filePath = readResourceFilePath(id);

		InputStream in = null;

		// if attachment in database
		if (filePath == null)
		{
			in = readResourceBodyDb(id);
		}

		// otherwise read the file
		else
		{
			in = readResourceBodyFs(filePath);
		}

		return in;
	}
}
