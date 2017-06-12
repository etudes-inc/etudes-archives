/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/PurgeMeleteHandler.java $
 * $Id: PurgeMeleteHandler.java 2847 2012-04-18 20:39:09Z ggolden $
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
 * Archives PurgeHandler for Melete. Note: Melete's table names are created in lower case.
 */
public class PurgeMeleteHandler implements PurgeHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(PurgeMeleteHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.melete";

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

		// get the modules for the site
		List<Integer> moduleIds = readCourseModules(siteId);

		// delete the "SHDATES" from all modules in the site
		fields[0] = siteId;
		delete(("DELETE MELETE_MODULE_SHDATES FROM MELETE_MODULE_SHDATES                                             "
				+ " INNER JOIN MELETE_COURSE_MODULE                                                                  "
				+ "   ON MELETE_MODULE_SHDATES.MODULE_ID = MELETE_COURSE_MODULE.MODULE_ID                            "
				+ " WHERE MELETE_COURSE_MODULE.COURSE_ID = ?").toLowerCase(), fields);

		// delete the site's preferences
		delete("DELETE FROM MELETE_SITE_PREFERENCE WHERE PREF_SITE_ID = ?".toLowerCase(), fields);

		// delete the course to module mappings for the site
		delete("DELETE FROM MELETE_COURSE_MODULE WHERE COURSE_ID = ?".toLowerCase(), fields);

		// process each module
		for (Integer moduleId : moduleIds)
		{
			// get the sections in the module
			List<Integer> sectionIds = readModuleSections(moduleId);
			for (Integer sectionId : sectionIds)
			{
				// delete the section to resource mappings for the section
				fields[0] = sectionId;
				delete("DELETE FROM MELETE_SECTION_RESOURCE WHERE SECTION_ID = ?".toLowerCase(), fields);

				// delete the tracking for the section
				delete("DELETE FROM MELETE_SECTION_TRACK_VIEW WHERE SECTION_ID = ?".toLowerCase(), fields);

				// delete bookmarks to the section
				delete("DELETE FROM MELETE_BOOKMARK WHERE SECTION_ID = ?".toLowerCase(), fields);
			}

			// delete the sections for the module
			fields[0] = moduleId;
			delete("DELETE FROM MELETE_SECTION WHERE MODULE_ID = ?".toLowerCase(), fields);

			// delete special access for the module
			delete("DELETE FROM MELETE_SPECIAL_ACCESS WHERE MODULE_ID = ?".toLowerCase(), fields);

			// delete the module
			delete("DELETE FROM MELETE_MODULE WHERE MODULE_ID = ?".toLowerCase(), fields);
		}

		// delete all the resources associated with the site
		fields[0] = "/private/meleteDocs/" + siteId + "/%";
		delete("DELETE FROM MELETE_RESOURCE WHERE RESOURCE_ID LIKE ?".toLowerCase(), fields);
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
	 * Find the module ids for the site
	 * 
	 * @param siteId
	 *        The siteId
	 * @return A List<Integer> of module ids associated with the site.
	 */
	protected List<Integer> readCourseModules(String siteId)
	{
		String sql = "SELECT MODULE_ID FROM MELETE_COURSE_MODULE WHERE COURSE_ID = ?".toLowerCase();
		Object[] fields = new Object[1];
		fields[0] = siteId;

		final List<Integer> rv = new ArrayList<Integer>();
		this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					String str = StringUtil.trimToNull(result.getString(1));
					if (str != null)
					{
						try
						{
							Integer id = Integer.valueOf(str);
							rv.add(id);
						}
						catch (NumberFormatException e)
						{
						}
					}

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("readCourseModules: " + e);
					return null;
				}
			}
		});

		return rv;
	}

	/**
	 * Find the sections for the module
	 * 
	 * @param moduleId
	 *        The moduleId
	 * @return A List<Integer> of section ids associated with the module.
	 */
	protected List<Integer> readModuleSections(Integer moduleId)
	{
		String sql = "SELECT SECTION_ID FROM MELETE_SECTION WHERE MODULE_ID = ?".toLowerCase();
		Object[] fields = new Object[1];
		fields[0] = moduleId;

		final List<Integer> rv = new ArrayList<Integer>();
		this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					String str = StringUtil.trimToNull(result.getString(1));
					if (str != null)
					{
						try
						{
							Integer id = Integer.valueOf(str);
							rv.add(id);
						}
						catch (NumberFormatException e)
						{
						}
					}

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("readModuleSections: " + e);
					return null;
				}
			}
		});

		return rv;
	}
}
