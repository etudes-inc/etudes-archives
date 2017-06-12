/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/XrefSyllabusHandler.java $
 * $Id: XrefSyllabusHandler.java 2823 2012-04-03 20:57:39Z ggolden $
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
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.XrefHandler;
import org.etudes.util.XrefHelper;
import org.etudes.util.api.Translation;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.util.StringUtil;

/**
 * Archives cross reference handler for Syllabus
 */
public class XrefSyllabusHandler implements XrefHandler
{
	protected class Syllabus
	{
		String asset;

		Long id;
	}

	/** Our log. */
	private static Log M_log = LogFactory.getLog(XrefSyllabusHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.syllabus";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: ContentHostingService. */
	protected ContentHostingService contentHostingService = null;

	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterXrefHandler(applicationId, this);
		M_log.info("destroy()");
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		this.archivesService.registerXrefHandler(applicationId, this);
		M_log.info("init()");
	}

	/**
	 * {@inheritDoc}
	 */
	public int removeXref(String siteId)
	{
		// ignore user sites
		if (siteId.startsWith("~")) return 0;

		M_log.info("xrefs for " + applicationId + " in site: " + siteId);

		int count = 0;

		// read all the syllabus information for the site
		List<Syllabus> syllabusData = readSyllabusData(siteId);
		for (Syllabus s : syllabusData)
		{
			String text = s.asset;

			// find the embedded cross-site document references
			Set<String> refs = XrefHelper.harvestEmbeddedReferences(text, siteId);
			count += refs.size();

			// copy them somewhere in the site
			List<Translation> translations = XrefHelper.importTranslateResources(refs, siteId, "Syllabus");

			// update text with the new locations
			String newText = XrefHelper.translateEmbeddedReferencesAndShorten(text, translations, siteId, null);

			// attachments
			List<String> attachmentIds = readAttachmentInfo(s.id);
			for (String attachmentId : attachmentIds)
			{
				try
				{
					// get the attachment
					ContentResource attachment = this.contentHostingService.getResource(attachmentId);

					// harvest any references to the site, translating as needed
					XrefHelper.harvestTranslateResource(attachment, siteId, "Syllabus");
				}
				catch (TypeException e)
				{
					M_log.warn("removeXref: " + e);
				}
				catch (IdUnusedException e)
				{
				}
				catch (PermissionException e)
				{
					M_log.warn("removeXref: " + e);
				}
			}

			if (StringUtil.different(text, newText))
			{
				// update
				Object[] fields = new Object[2];
				fields[0] = newText;
				fields[1] = s.id;
				write("UPDATE SAKAI_SYLLABUS_DATA SET ASSET = ? WHERE ID = ?", fields);
			}
		}

		return count;
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
	 * Set the ContentHostingService.
	 * 
	 * @param service
	 *        The ContentHostingService.
	 */
	public void setContentHostingService(ContentHostingService service)
	{
		this.contentHostingService = service;
	}

	/**
	 * {@inheritDoc}
	 */
	public void setSqlService(SqlService service)
	{
		this.sqlService = service;
	}

	/**
	 * Read the syllabus attachments for this syllabus data (as CHS ids).
	 * 
	 * @param sdId
	 *        The syllabus data Id
	 * @return List<String> for each attachment for the syllabus data.
	 */
	protected List<String> readAttachmentInfo(final Long sdId)
	{
		String sql = "SELECT ATTACHMENTID FROM SAKAI_SYLLABUS_ATTACH WHERE SYLLABUSID = ?";
		Object[] fields = new Object[1];
		fields[0] = sdId;

		List<String> rv = this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					String id = StringUtil.trimToNull(result.getString(1));
					// /attachment/<site>/Syllabus/21c64202-b906-4c81-8029-0ac4f37a0f29/http:__www.etudes.org
					return id;
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

	/**
	 * Read syllabus data.
	 * 
	 * @param siteId
	 *        The context site Id
	 * @return List<Syllabus> for each syllabus data item found.
	 */
	protected List<Syllabus> readSyllabusData(final String siteId)
	{
		String sql = "SELECT SD.ID, SD.ASSET FROM SAKAI_SYLLABUS_DATA SD                                                 "
				+ " INNER JOIN SAKAI_SYLLABUS_ITEM SI ON SD.SURROGATEKEY = SI.ID                                         "
				+ " WHERE SI.CONTEXTID = ?";
		Object[] fields = new Object[1];
		fields[0] = siteId;

		List<Syllabus> rv = this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					String str = StringUtil.trimToNull(result.getString(1));
					if (str == null) return null;
					try
					{
						Long id = Long.valueOf(str);
						String asset = StringUtil.trimToNull(result.getString(2));
						Syllabus s = new Syllabus();
						s.id = id;
						s.asset = asset;
						return s;
					}
					catch (NumberFormatException e)
					{
					}

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("readSyllabusData: " + e);
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

	/**
	 * Do a write query.
	 * 
	 * @param query
	 *        The delete query.
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
	 * Do a write query (transaction code)
	 * 
	 * @param query
	 *        The delete query.
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
