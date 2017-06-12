/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/PurgeUserUserHandler.java $
 * $Id: PurgeUserUserHandler.java 3011 2012-06-20 05:05:48Z ggolden $
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
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.user.api.UserLockedException;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.UserPermissionException;

/**
 * Archives PurgeUserHandler for the main User data
 */
public class PurgeUserUserHandler implements PurgeUserHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(PurgeUserUserHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.user";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: UserDirectoryService. */
	protected UserDirectoryService userDirectoryService = null;

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
			UserEdit user = this.userDirectoryService.editUser(userId);
			this.userDirectoryService.removeUser(user);
		}
		catch (UserNotDefinedException e)
		{
			M_log.warn("purge: user id: " + userId + " : " + e.toString());
		}
		catch (UserPermissionException e)
		{
			M_log.warn("purge: user id: " + userId + " : " + e.toString());
		}
		catch (UserLockedException e)
		{
			M_log.warn("purge: user id: " + userId + " : " + e.toString());
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
	public void setUserDirectoryService(UserDirectoryService service)
	{
		this.userDirectoryService = service;
	}
}
