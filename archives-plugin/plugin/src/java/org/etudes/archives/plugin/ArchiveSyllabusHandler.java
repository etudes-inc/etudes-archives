/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiveSyllabusHandler.java $
 * $Id: ArchiveSyllabusHandler.java 8238 2014-06-12 18:29:58Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2009, 2010, 2011, 2012, 2014 Etudes, Inc.
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
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchiveHandler;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.util.XrefHelper;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.util.StringUtil;

/**
 * Archives archive handler for Syllabus
 */
public class ArchiveSyllabusHandler implements ArchiveHandler
{
	protected class Syllabus
	{
		Long id;

		String redirectUrl;
		
		Boolean openWindow;
	}

	protected class SyllabusData
	{
		String asset;

		Long id;

		Integer position;

		String pubview;

		String status;

		String title;
	}

	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchiveSyllabusHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.syllabus";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/**
	 * {@inheritDoc}
	 */
	public void archive(String siteId, Archive archive)
	{
		M_log.info("archive " + applicationId + " in site: " + siteId);

		// read the syllabus data for the site
		List<Syllabus> syllabus = readSyllabus(siteId);
		for (Syllabus s : syllabus)
		{
			// if a redirect...
			if (s.redirectUrl != null)
			{
				// make an artifact
				Artifact artifact = archive.newArtifact(applicationId, "/syllabus/0");

				artifact.getProperties().put("redirect", s.redirectUrl);
				artifact.getProperties().put("openWindow", s.openWindow);

				// archive it
				archive.archive(artifact);
			}

			// also get the items
			List<SyllabusData> syllabusData = readSyllabusData(s.id);
			for (SyllabusData sd : syllabusData)
			{
				// find the embedded document references
				Set<String> refs = XrefHelper.harvestEmbeddedReferences(sd.asset, null);

				// get the attachments; combine with the references and make a reference string list for archiving
				List<String> attachments = readAttachmentInfo(sd.id);
				refs.addAll(attachments);

				// make an artifact - use the position as part of the reference
				Artifact artifact = archive.newArtifact(applicationId, "/syllabus/" + sd.position);

				// record the references
				artifact.getReferences().addAll(refs);

				// set the syllabus information
				artifact.getProperties().put("position", sd.position);
				artifact.getProperties().put("body", sd.asset);
				artifact.getProperties().put("title", sd.title);
				artifact.getProperties().put("pubview", sd.pubview);
				artifact.getProperties().put("status", sd.status);
				artifact.getProperties().put("attachments", attachments);

				// archive it
				archive.archive(artifact);
			}
		}
	}

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterArchiveHandler(this);
		M_log.info("destroy()");
	}

	/**
	 * {@inheritDoc}
	 */
	public String getApplicationId()
	{
		return applicationId;
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		this.archivesService.registerArchiveHandler(this);
		M_log.info("init()");
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
	 * Make an integer from a possibly null string.
	 * 
	 * @param str
	 *        The string.
	 * @return The integer.
	 */
	protected Integer integerValue(String str)
	{
		if (str == null) return null;
		try
		{
			return Integer.valueOf(str);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
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
	 * Read the syllabus attachments for this syllabus data.
	 * 
	 * @param sdId
	 *        The syllabus data Id
	 * @return List<String> for each attachment for the syllabus data.
	 */
	@SuppressWarnings("unchecked")
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
					return "/content" + id;
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
	 * Read syllabus items for this site.
	 * 
	 * @param siteId
	 *        The context site Id
	 * @return List<Syllabus> for the syllabus items found in the site.
	 */
	@SuppressWarnings("unchecked")
	protected List<Syllabus> readSyllabus(final String siteId)
	{
		String sql = "SELECT SI.ID, SI.REDIRECTURL, SI.OPENWINDOW FROM SAKAI_SYLLABUS_ITEM SI WHERE SI.CONTEXTID = ?";
		Object[] fields = new Object[1];
		fields[0] = siteId;

		List<Syllabus> rv = this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					String id = StringUtil.trimToNull(result.getString(1));
					String redirectUrl = StringUtil.trimToNull(result.getString(2));

					Syllabus s = new Syllabus();
					s.id = longValue(id);
					s.redirectUrl = redirectUrl;
					s.openWindow = result.getBoolean(3);

					return s;

				}
				catch (SQLException e)
				{
					M_log.warn("readSyllabus: " + e.toString());
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
	 * @param id
	 *        The syllabus Id
	 * @return List<SyllabusData> for each syllabus data for the syllabus.
	 */
	@SuppressWarnings("unchecked")
	protected List<SyllabusData> readSyllabusData(final Long id)
	{
		String sql = "SELECT SD.ID, SD.ASSET, SD.POSITION_C, SD.XVIEW, SD.STATUS, SD.TITLE FROM SAKAI_SYLLABUS_DATA SD WHERE SD.SURROGATEKEY = ?";
		Object[] fields = new Object[1];
		fields[0] = id;

		List<SyllabusData> rv = this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					String id = StringUtil.trimToNull(result.getString(1));
					String asset = StringUtil.trimToNull(result.getString(2));
					Integer position = integerValue(StringUtil.trimToNull(result.getString(3)));
					String pubview = StringUtil.trimToNull(result.getString(4));
					String status = StringUtil.trimToNull(result.getString(5));
					String title = StringUtil.trimToNull(result.getString(6));
					
					SyllabusData s = new SyllabusData();
					s.id = longValue(id);
					s.asset = asset;
					s.position = position;
					s.pubview = pubview;
					s.status = status;
					s.title = title;
					return s;

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

		return rv;
	}
}
