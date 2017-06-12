/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiverHandler.java $
 * $Id: ArchiverHandler.java 2929 2012-05-16 21:01:57Z ggolden $
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

import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.Archiver;
import org.etudes.archives.api.ArchivesService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.util.StringUtil;

/**
 * ArchiverHandler
 */
public class ArchiverHandler implements Archiver
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchiverHandler.class);

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: ServerConfigurationService */
	protected ServerConfigurationService serverConfigurationService = null;

	/** Dependency: SiteTermHandler */
	protected SiteTermHandler siteTermHandler = null;

	/** Configuration: the root file path for archives. */
	protected String storagePath = null;

	/**
	 * {@inheritDoc}
	 */
	public void deleteArchive(String siteId)
	{
		String filePath = filePath(siteId);
		String zipName = filePath.substring(0, filePath.length() - 1) + ".zip";

		// delete the file if present
		File zip = new File(zipName);
		if (zip.exists())
		{
			zip.delete();
		}
		else
		{
			M_log.warn("deleteArchive: missing file: " + zipName);
		}
	}

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterArchiver(this);
		M_log.info("destroy()");
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		this.archivesService.registerArchiver(this);
		this.storagePath = StringUtil.trimToNull(this.serverConfigurationService.getString("archives.path"));
		if ((this.storagePath != null) && (!this.storagePath.endsWith("/"))) this.storagePath += "/";

		M_log.info("init(): storagePath: " + this.storagePath);
	}

	/**
	 * {@inheritDoc}
	 */
	public Archive newArchive(String siteId)
	{
		ArchiveImpl rv = new ArchiveImpl();
		rv.setSiteId(siteId);

		// store it here
		String filePath = filePath(siteId);
		rv.setFilePath(filePath);

		// get the archives ready
		rv.init();

		return rv;
	}

	/**
	 * {@inheritDoc}
	 */
	public Archive readArchive(String siteId)
	{
		ArchiveImpl rv = new ArchiveImpl();
		rv.setSiteId(siteId);

		// find it here
		String filePath = filePath(siteId);
		rv.setFilePath(filePath);

		rv.read();

		return rv;
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
	 * Form the folder path to the archive folder for this site.
	 * 
	 * @param siteId
	 *        The site id.
	 * @return The file path to the archive for this site.
	 */
	protected String filePath(String siteId)
	{
		Long term = this.siteTermHandler.getSiteTerm(siteId);
		String rv = this.storagePath + term.toString() + "/" + siteId + "/";
		return rv;
	}
}
