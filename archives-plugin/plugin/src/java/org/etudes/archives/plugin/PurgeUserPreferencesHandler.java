/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/PurgeUserPreferencesHandler.java $
 * $Id: PurgeUserPreferencesHandler.java 3054 2012-06-27 21:12:10Z ggolden $
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.PurgeUserHandler;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.user.api.PreferencesEdit;
import org.sakaiproject.user.api.PreferencesService;

/**
 * Archives PurgeUserHandler for the user (Sakai) preference data
 */
public class PurgeUserPreferencesHandler implements PurgeUserHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(PurgeUserPreferencesHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.preferences";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: PreferencesService. */
	protected PreferencesService preferencesService = null;

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
		M_log.info("init()");
	}

	/**
	 * {@inheritDoc}
	 */
	public void purge(String userId)
	{
		M_log.info("purge user " + applicationId + " for user: " + userId);

		try
		{
			PreferencesEdit prefs = this.preferencesService.edit(userId);
			this.preferencesService.remove(prefs);
		}
		catch (PermissionException e)
		{
			M_log.warn("purge: user id: " + userId + " : " + e.toString());
		}
		catch (InUseException e)
		{
			M_log.warn("purge: user id: " + userId + " : " + e.toString());
		}
		catch (IdUnusedException e)
		{
			// M_log.warn("purge: user id: " + userId + " : " + e.toString());
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
	public void setPreferencesService(PreferencesService service)
	{
		this.preferencesService = service;
	}
}
