/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/SiteTermHandler.java $
 * $Id: SiteTermHandler.java 3048 2012-06-26 03:37:23Z ggolden $
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
import org.etudes.archives.api.Subset;
import org.etudes.archives.api.TermHandler;
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
public class SiteTermHandler implements TermHandler
{
	protected class Term
	{
		Integer id;

		String suffix;
	}

	/** Our log. */
	private static Log M_log = LogFactory.getLog(SiteTermHandler.class);

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Configuration: to run the ddl on init or not. */
	protected boolean autoDdl = false;

	/** Dependency: ServerConfigurationService */
	protected ServerConfigurationService serverConfigurationService = null;

	/** Dependency: SiteService */
	protected SiteService siteService = null;

	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterTermHandler(this);
		M_log.info("destroy()");
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public List<String> getAllTermSiteIds(String termId, Subset page, String institutionCode)
	{
		String sql = null;
		if ((institutionCode == null) || (institutionCode.length() == 0))
		{
			sql = "SELECT ST.SITE_ID FROM ARCHIVES_SITE_TERM ST INNER JOIN ARCHIVES_TERM T ON ST.TERM_ID = T.ID WHERE T.TERM = ? ORDER BY ST.ID ASC LIMIT ?,?";
		}
		else
		{
			// add criteria to limit by site title prefix - get title from site or from the purged table, to get existing and purged sites
			sql = "SELECT ST.SITE_ID FROM ARCHIVES_SITE_TERM ST INNER JOIN ARCHIVES_TERM T ON ST.TERM_ID = T.ID "
					+ "LEFT OUTER JOIN SAKAI_SITE S ON ST.SITE_ID = S.SITE_ID LEFT OUTER JOIN ARCHIVES_SITES_PURGED P ON ST.SITE_ID = P.SITE_ID"
					+ " WHERE T.TERM = ? AND ((S.TITLE IS NOT NULL AND S.TITLE LIKE '" + institutionCode.toUpperCase()
					+ " %') OR (P.TITLE IS NOT NULL AND P.TITLE LIKE '" + institutionCode.toUpperCase() + " %')) ORDER BY ST.ID ASC LIMIT ?,?";
		}

		Object[] fields = new Object[3];
		fields[0] = termId;
		fields[1] = page.getOffset();
		fields[2] = page.getSize();

		List<String> rv = this.sqlService.dbRead(sql.toString(), fields, null);

		return rv;
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public Long getSiteTerm(String siteId)
	{
		String sql = "SELECT TERM_ID FROM ARCHIVES_SITE_TERM WHERE SITE_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = siteId;

		List<String> rv = this.sqlService.dbRead(sql.toString(), fields, null);

		if ((rv == null) || rv.isEmpty()) return Long.valueOf(1);

		return Long.valueOf(rv.get(0));
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public List<String> getTermSiteIds(String termId, Subset page, String institutionCode)
	{
		// join with site to weed out any sites that don't currently exist
		String sql = null;
		if ((institutionCode == null) || (institutionCode.length() == 0))
		{
			sql = "SELECT ST.SITE_ID FROM ARCHIVES_SITE_TERM ST INNER JOIN ARCHIVES_TERM T ON ST.TERM_ID = T.ID "
					+ "INNER JOIN SAKAI_SITE S ON ST.SITE_ID = S.SITE_ID WHERE T.TERM = ? ORDER BY ST.ID ASC LIMIT ?,?";
		}
		else
		{
			// add criteria to limit by site title prefix
			sql = "SELECT ST.SITE_ID FROM ARCHIVES_SITE_TERM ST INNER JOIN ARCHIVES_TERM T ON ST.TERM_ID = T.ID "
					+ "INNER JOIN SAKAI_SITE S ON ST.SITE_ID = S.SITE_ID WHERE T.TERM = ? AND S.TITLE LIKE '" + institutionCode.toUpperCase()
					+ " %' ORDER BY ST.ID ASC LIMIT ?,?";
		}
		Object[] fields = new Object[3];
		fields[0] = termId;
		fields[1] = page.getOffset();
		fields[2] = page.getSize();

		List<String> rv = this.sqlService.dbRead(sql.toString(), fields, null);

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
			this.sqlService.ddl(this.getClass().getClassLoader(), "archives_site_term");
		}

		this.archivesService.registerTermHandler(this);

		M_log.info("init(): autoDdl: " + this.autoDdl);
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
	 * {@inheritDoc}
	 */
	public void setSiteTerm(String siteId)
	{
		try
		{
			// get the site
			Site site = this.siteService.getSite(siteId);

			// figure out the term
			Integer termId = computeTerm(site);

			// get the current site term record
			Integer curTermId = readTerm(siteId);

			Object[] fields = new Object[2];
			fields[0] = termId;
			fields[1] = siteId;

			// add if missing
			if (curTermId == null)
			{
				write("INSERT INTO ARCHIVES_SITE_TERM (TERM_ID, SITE_ID) VALUES (?,?)", fields);
			}

			// update if needed
			else if (!curTermId.equals(termId))
			{
				write("UPDATE ARCHIVES_SITE_TERM SET TERM_ID = ? WHERE SITE_ID = ?", fields);
			}
		}
		catch (IdUnusedException e)
		{
			M_log.warn("contextCreated: " + e.toString());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void setSqlService(SqlService service)
	{
		this.sqlService = service;
	}

	/**
	 * Figure out the term for this site.
	 * 
	 * @param site
	 *        The site.
	 * @return The site's term.
	 */
	protected Integer computeTerm(Site site)
	{
		// unknown is term 1, deleted is term 2, user is term 3, system is term 4, project is term 5
		if (this.siteService.isSpecialSite(site.getId()) || ("!admin".equals(site.getId())) || ("mercury".equals(site.getId())))
		{
			return Integer.valueOf(4);
		}

		if (this.siteService.isUserSite(site.getId()))
		{
			return Integer.valueOf(3);
		}

		if ("project".equals(site.getType()))
		{
			return Integer.valueOf(5);
		}

		// check the title against the registered suffixes
		String title = site.getTitle();
		List<Term> terms = readTerms();
		for (Term t : terms)
		{
			if (t.suffix == null) continue;
			if (title.endsWith(t.suffix)) return t.id;
		}

		return Integer.valueOf(1);
	}

	/**
	 * Read the term for this site
	 * 
	 * @param siteId
	 *        The site Id
	 * @return The site's term, if it is in the table, or null if not.
	 */
	@SuppressWarnings("unchecked")
	protected Integer readTerm(String siteId)
	{
		String sql = "SELECT TERM_ID FROM ARCHIVES_SITE_TERM WHERE SITE_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = siteId;

		List<String> rv = this.sqlService.dbRead(sql.toString(), fields, null);
		if (rv.isEmpty()) return null;
		if (rv.size() > 1)
		{
			M_log.warn("readTerm: site has multiple site_term records: " + siteId);
		}

		return Integer.valueOf(rv.get(0));
	}

	/**
	 * Read the defined terms
	 * 
	 * @return The defined terms.
	 */
	@SuppressWarnings("unchecked")
	protected List<Term> readTerms()
	{
		String sql = "SELECT ID, SUFFIX FROM ARCHIVES_TERM";
		List<Term> rv = this.sqlService.dbRead(sql.toString(), null, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					String str = StringUtil.trimToNull(result.getString(1));
					Integer id = null;
					if (str == null) return null;
					try
					{
						id = Integer.valueOf(str);
					}
					catch (NumberFormatException e)
					{
						M_log.warn("readTerms: " + e.toString());
						return null;
					}

					String suffix = StringUtil.trimToNull(result.getString(2));

					Term t = new Term();
					t.id = id;
					t.suffix = suffix;

					return t;
				}
				catch (SQLException e)
				{
					M_log.warn("readTerms: " + e);
					return null;
				}
			}
		});
		return rv;
	}

	/**
	 * Run a writing (insert, update) query.
	 * 
	 * @param query
	 *        The query.
	 * @param fields
	 *        the prepared statement fields.
	 */
	protected void write(final String query, final Object[] fields)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				writeTx(query, fields);
			}
		}, "write: " + fields[0] + " " + query);
	}

	/**
	 * Do a delete (transaction code)
	 * 
	 * @param query
	 *        The query.
	 * @param fields
	 *        the prepared statement fields.
	 */
	protected void writeTx(String query, Object[] fields)
	{
		if (!this.sqlService.dbWrite(query, fields))
		{
			throw new RuntimeException("writeTx: db write failed: " + fields[0] + " " + query);
		}
	}

}
