/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/RecorderHandler.java $
 * $Id: RecorderHandler.java 3048 2012-06-26 03:37:23Z ggolden $
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
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchiveDescription;
import org.etudes.archives.api.ArchivesRecorder;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Subset;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.util.StringUtil;

/**
 * RecorderHandler
 */
public class RecorderHandler implements ArchivesRecorder
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(RecorderHandler.class);

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Configuration: to run the ddl on init or not. */
	protected boolean autoDdl = false;

	/** Dependency: ServerConfigurationService */
	protected ServerConfigurationService serverConfigurationService = null;

	/** Dependency: SiteService */
	protected SiteService siteService = null;

	/** Dependency: SiteTermHandler */
	protected SiteTermHandler siteTermHandler = null;

	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterRecorder(this);
		M_log.info("destroy()");
	}

	/**
	 * {@inheritDoc}
	 */
	public List<ArchiveDescription> getTermArchives(String termId, Subset page, String institutionCode)
	{
		final List<ArchiveDescription> rv = new ArrayList<ArchiveDescription>();

		String sql = null;
		if ((institutionCode == null) || (institutionCode.length() == 0))
		{
			sql = "SELECT A.ID, A.DATE_ARCHIVED, A.SITE_ID, A.TERM_ID, T.TERM, A.TITLE                                  "
					+ " FROM ARCHIVES_SITES_ARCHIVED A                                                                  "
					+ " INNER JOIN ARCHIVES_TERM T ON A.TERM_ID = T.ID                                                  "
					+ " WHERE T.TERM = ? ORDER BY A.TERM_ID DESC, A.TITLE ASC LIMIT ?,?                                 ";
		}
		else
		{
			sql = "SELECT A.ID, A.DATE_ARCHIVED, A.SITE_ID, A.TERM_ID, T.TERM, A.TITLE                                  "
					+ " FROM ARCHIVES_SITES_ARCHIVED A                                                                  "
					+ " INNER JOIN ARCHIVES_TERM T ON A.TERM_ID = T.ID                                                  "
					+ " WHERE T.TERM = ? AND A.TITLE LIKE '" + institutionCode.toUpperCase()
					+ " %' ORDER BY A.TERM_ID DESC, A.TITLE ASC LIMIT ?,?                                               ";

		}
		Object[] fields = new Object[3];
		fields[0] = termId;
		fields[1] = page.getOffset();
		fields[2] = page.getSize();

		this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					ArchiveDescriptionImpl desc = new ArchiveDescriptionImpl();
					desc.setId(longValue(StringUtil.trimToNull(result.getString(1))));
					desc.setDate(readDate(result, 2));
					desc.setSiteId(StringUtil.trimToNull(result.getString(3)));
					desc.setTerm(longValue(StringUtil.trimToNull(result.getString(4))));
					desc.setTermDescription(StringUtil.trimToNull(result.getString(5)));
					desc.setTitle(StringUtil.trimToNull(result.getString(6)));

					rv.add(desc);

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("getUserArchives: " + e);
				}
				catch (IndexOutOfBoundsException e)
				{
					M_log.warn("getUserArchives: " + e);
				}
				return null;
			}
		});

		return rv;
	}

	/**
	 * {@inheritDoc}
	 */
	public List<ArchiveDescription> getUserArchives(String userId)
	{
		final List<ArchiveDescription> rv = new ArrayList<ArchiveDescription>();

		String sql = "SELECT A.ID, A.DATE_ARCHIVED, A.SITE_ID, A.TERM_ID, T.TERM, A.TITLE                           "
				+ " FROM ARCHIVES_OWNERS O                                                                          "
				+ " INNER JOIN ARCHIVES_SITES_ARCHIVED A ON O.ARCHIVES_ID = A.ID                                    "
				+ " INNER JOIN ARCHIVES_TERM T ON A.TERM_ID = T.ID                                                  "
				+ " WHERE O.USER_ID = ? ORDER BY A.TERM_ID DESC, A.TITLE ASC ";
		Object[] fields = new Object[1];
		fields[0] = userId;

		this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					ArchiveDescriptionImpl desc = new ArchiveDescriptionImpl();
					desc.setId(longValue(StringUtil.trimToNull(result.getString(1))));
					desc.setDate(readDate(result, 2));
					desc.setSiteId(StringUtil.trimToNull(result.getString(3)));
					desc.setTerm(longValue(StringUtil.trimToNull(result.getString(4))));
					desc.setTermDescription(StringUtil.trimToNull(result.getString(5)));
					desc.setTitle(StringUtil.trimToNull(result.getString(6)));

					rv.add(desc);

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("getUserArchives: " + e);
				}
				catch (IndexOutOfBoundsException e)
				{
					M_log.warn("getUserArchives: " + e);
				}
				return null;
			}
		});

		return rv;
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		// check for auto.ddl in config - missing implies on
		// Note: webapp loaded components don't have access to the <value>${auto.ddl}</value> feature
		String autoDdl = StringUtil.trimToNull(this.serverConfigurationService.getString("auto.ddl"));
		if (autoDdl == null)
		{
			this.autoDdl = true;
		}
		else
		{
			this.autoDdl = Boolean.valueOf(autoDdl).booleanValue();
		}

		// if we are auto-creating our schema, check and create
		if (this.autoDdl)
		{
			this.sqlService.ddl(this.getClass().getClassLoader(), "archives_recorder");
		}

		this.archivesService.registerRecorder(this);

		M_log.info("init(): autoDdl: " + this.autoDdl);
	}

	/**
	 * {@inheritDoc}
	 */
	public void recordArchive(final String siteId)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				recordArchiveTx(siteId);
			}
		}, "recordArchive: " + siteId);
	}

	/**
	 * {@inheritDoc}
	 */
	public void recordArchiveDelete(final String siteId)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				recordArchiveDeleteTx(siteId);
			}
		}, "recordArchive: " + siteId);
	}

	/**
	 * * {@inheritDoc}
	 */
	public void recordPurge(final String siteId)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				recordPurgeTx(siteId);
			}
		}, "recordPurge: " + siteId);
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
	 * Configuration: to run the ddl on init or not.
	 * 
	 * @param value
	 *        the auto ddl value.
	 */
	public void setAutoDdl(String value)
	{
		this.autoDdl = Boolean.valueOf(value).booleanValue();
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
	 * Dependency: SiteService.
	 * 
	 * @param service
	 *        The SiteService.
	 */
	public void setSiteService(SiteService service)
	{
		this.siteService = service;
	}

	/**
	 * Set the SiteTermHandler.
	 * 
	 * @param service
	 *        The TimeService.
	 */
	public void setSiteTermHandler(SiteTermHandler handler)
	{
		this.siteTermHandler = handler;
	}

	/**
	 * {@inheritDoc}
	 */
	public void setSqlService(SqlService service)
	{
		this.sqlService = service;
	}

	/**
	 * Make a long from a possibly null string.
	 * 
	 * @param str
	 *        The string.
	 * @return The long.
	 */
	protected Long longValue(String str)
	{
		if (str == null) return null;
		try
		{
			return Long.valueOf(str);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/**
	 * Read a long from the result set, and convert to a null (if 0) or a Date.
	 * 
	 * @param results
	 *        The result set.
	 * @param index
	 *        The index.
	 * @return The Date or null.
	 * @throws SQLException
	 */
	protected Date readDate(ResultSet result, int index) throws SQLException
	{
		long time = result.getLong(index);
		if (time == 0) return null;
		return new Date(time);
	}

	/**
	 * Transaction for recordArchiveDelete
	 */
	protected void recordArchiveDeleteTx(String siteId)
	{
		// remove any record for the archiving of this site
		String sql = "DELETE ARCHIVES_OWNERS                                                                                          "
				+ " FROM ARCHIVES_OWNERS INNER JOIN ARCHIVES_SITES_ARCHIVED ON ARCHIVES_OWNERS.ARCHIVES_ID = ARCHIVES_SITES_ARCHIVED.ID"
				+ " WHERE ARCHIVES_SITES_ARCHIVED.SITE_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = siteId;
		if (!this.sqlService.dbWrite(sql, fields))
		{
			throw new RuntimeException("recordArchivePurgeTx: db write failed: " + fields[0] + " " + sql);
		}

		sql = "DELETE FROM ARCHIVES_SITES_ARCHIVED WHERE SITE_ID = ?";
		if (!this.sqlService.dbWrite(sql, fields))
		{
			throw new RuntimeException("recordArchivePurgeTx: db write failed: " + fields[0] + " " + sql);
		}
	}

	/**
	 * Transaction for recordArchive
	 */
	@SuppressWarnings("unchecked")
	protected void recordArchiveTx(String siteId)
	{
		// remove any old record for the previous archiving of this site
		recordArchiveDeleteTx(siteId);

		// add a new archives entry for the site
		String sql = "INSERT INTO ARCHIVES_SITES_ARCHIVED (SITE_ID, DATE_ARCHIVED, TITLE, TERM_ID) VALUES (?,?,?,?)";
		Object[] fields = new Object[4];
		fields[0] = siteId;
		fields[1] = System.currentTimeMillis();
		fields[2] = null;
		fields[3] = null;

		// make the archive available to all maintain role users
		Set<String> ownersUserIds = null;
		String maintainRole = null;

		// get the site information
		try
		{
			Site site = this.siteService.getSite(siteId);
			fields[2] = site.getTitle();

			maintainRole = site.getMaintainRole();
			ownersUserIds = site.getUsersHasRole(maintainRole);

			// try with "Instructor" if we didn't get anyone
			if ((ownersUserIds == null) || (ownersUserIds.isEmpty()))
			{
				ownersUserIds = site.getUsersHasRole("Instructor");
			}

			Long term = this.siteTermHandler.getSiteTerm(siteId);
			fields[3] = term;
		}
		catch (IdUnusedException e)
		{
			// set term to unknown
			fields[3] = Long.valueOf(1);
		}

		// insert the archives record
		Long id = this.sqlService.dbInsert(null, sql, fields, "ID");
		if (id == null)
		{
			throw new RuntimeException("recordArchiveTx: dbInsert failed");
		}

		// insert a record for each owner
		if ((ownersUserIds != null) && (!ownersUserIds.isEmpty()))
		{
			sql = "INSERT INTO ARCHIVES_OWNERS (ARCHIVES_ID, USER_ID) VALUES (?,?)";
			fields = new Object[2];
			fields[0] = id;
			for (String uid : ownersUserIds)
			{
				fields[1] = uid;
				this.sqlService.dbWrite(sql, fields);
			}
		}
		else
		{
			M_log.warn("recordArchiveTx: site has no owners: " + maintainRole + " " + siteId);
		}
	}

	/**
	 * Transaction code for recordPurge
	 */
	protected void recordPurgeTx(String siteId)
	{
		// read what was there, if anything
		final String[] info = new String[2];
		info[0] = info[1] = null;

		String sql = "SELECT P.TITLE, P.TERM_ID FROM ARCHIVES_SITES_PURGED P WHERE P.SITE_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = siteId;

		this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					info[0] = result.getString(1);
					info[1] = result.getString(2);

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("recordPurgeTx: " + e);
				}
				catch (IndexOutOfBoundsException e)
				{
					M_log.warn("recordPurgeTx: " + e);
				}
				return null;
			}
		});

		// make room for the new record
		sql = "DELETE FROM ARCHIVES_SITES_PURGED WHERE SITE_ID = ?";
		if (!this.sqlService.dbWrite(sql, fields))
		{
			throw new RuntimeException("recordPurgeTx: db write failed: " + fields[0] + " " + sql);
		}

		// add the new record
		sql = "INSERT INTO ARCHIVES_SITES_PURGED (SITE_ID, DATE_PURGED, TITLE, TERM_ID) VALUES (?,?,?,?)";
		fields = new Object[4];
		fields[0] = siteId;
		fields[1] = System.currentTimeMillis();
		fields[2] = null;
		fields[3] = null;

		// get the site information
		try
		{
			Site site = this.siteService.getSite(siteId);
			fields[2] = site.getTitle();

			Long term = this.siteTermHandler.getSiteTerm(siteId);
			fields[3] = term;
		}
		catch (IdUnusedException e)
		{
			// use any info we picked up from before
			if (info[0] != null)
			{
				fields[2] = info[0];
				fields[3] = Long.parseLong(info[1]);
			}

			else
			{
				// set term to unknown, leave title null
				fields[3] = Long.valueOf(1);
			}
		}

		this.sqlService.dbWrite(sql, fields);
	}
}
