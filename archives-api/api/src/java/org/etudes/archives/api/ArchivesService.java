/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-api/api/src/java/org/etudes/archives/api/ArchivesService.java $
 * $Id: ArchivesService.java 7884 2014-04-24 22:25:32Z ggolden $
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

package org.etudes.archives.api;

import java.util.List;
import java.util.Set;

import org.sakaiproject.exception.PermissionException;

/**
 * ArchivesService...
 */
public interface ArchivesService
{
	/**
	 * Archive the data for the site.
	 * 
	 * @param siteId
	 *        The site id.
	 * @throws PermissionException
	 *         if the user is not permitted.
	 */
	void archiveSite(String siteId) throws PermissionException;

	/**
	 * Archive the data for the sites in this term.
	 * 
	 * @param termId
	 *        The term id.
	 * @param page
	 *        Which subset of the term to archive.
	 * @param institutionCode
	 *        The institution code (lower case) to limit the action to - ignore if null. if the user is not permitted.
	 * @throws PermissionException
	 */
	void archiveTerm(String termId, Subset page, String institutionCode) throws PermissionException;

	/**
	 * Delete the archived data for the site.
	 * 
	 * @param siteId
	 *        The site id.
	 * @throws PermissionException
	 *         if the user is not permitted.
	 */
	void deleteSiteArchive(String siteId) throws PermissionException;

	/**
	 * Delete the archived data for the sites in this term.
	 * 
	 * @param termId
	 *        The term id.
	 * @param page
	 *        Which subset of the term to purge.
	 * @param institutionCode
	 *        The institution code (lower case) to limit the action to - ignore if null. if the user is not permitted.
	 * @throws PermissionException
	 *         if the user is not permitted.
	 */
	void deleteTermArchive(String termId, Subset page, String institutionCode) throws PermissionException;

	/**
	 * Access the registered handler for purging attachments.
	 * 
	 * @return The PurgeAttachmentHandler if we have one, null if not.
	 */
	PurgeAttachmentHandler getPurgeAttachmentHandler();

	/**
	 * Get the status of the running task.
	 * 
	 * @return The running task status message, or null if there's no running task.
	 */
	String getTaskStatus();

	/**
	 * Get the term handler. Note: call as needed; the term handler may change dynamically, so don't hold the reference.
	 * 
	 * @return The term handler.
	 */
	TermHandler getTermHandler();

	/**
	 * Check which tools have data available for import in the archive.
	 * 
	 * @param archiveSiteId
	 *        The archive id.
	 * @return a Set of well known ids for tools that have data in the archive.
	 */
	Set<String> getToolIds(String archiveSiteId);

	/**
	 * Find all the archives that this user has access to. Sort by term and then site title.
	 * 
	 * @param userId
	 *        The user id.
	 * @return A List of ArchiveDescriptions for each archive the user has access to, possibly empty.
	 */
	List<ArchiveDescription> getUserArchives(String userId);

	/**
	 * Import an archived data set into this site.
	 * 
	 * @param archiveSiteId
	 *        The archive site id.
	 * @param siteId
	 *        The site id.
	 * @param asUserId
	 *        The user id to run the import as - optional, only works if the current user is a super user.
	 * @throws PermissionException
	 *         if the user is not permitted.
	 */
	void importSite(String archiveSiteId, String siteId, String asUserId) throws PermissionException;

	/**
	 * Import an archived data set into this site. Do it on this thread.
	 * 
	 * @param archiveSiteId
	 *        The archive data id.
	 * @param siteId
	 *        The site id.
	 * @param importToolIds
	 *        A Set of tool ids (i.e. sakai.announcements) to import.
	 * @throws PermissionException
	 *         if the user is not permitted.
	 */
	void importSiteImmediate(String archiveSiteId, String siteId, Set<String> importToolIds) throws PermissionException;

	/**
	 * Check if a task is currently running.
	 * 
	 * @return true if a task is running, false if not.
	 */
	boolean isTaskRunning();

	/**
	 * List the archived data for the site.
	 * 
	 * @param siteId
	 *        The site id.
	 * @throws PermissionException
	 *         if the user is not permitted.
	 * @return an Html display of the data archived.
	 */
	void listArchive(String siteId) throws PermissionException;

	/**
	 * List all users who have no site membership (other than possibly their myworkspace).
	 * 
	 * @param limit
	 *        List the first n qualifying users - if null, list them all.
	 * @throws PermissionException
	 *         if the user is not permitted to do this.
	 */
	void listInactiveUsers(Integer limit) throws PermissionException;

	/**
	 * Purge the data for all users who have no site membership (other than possibly their myworkspace).
	 * 
	 * @param limit
	 *        Purge the first n qualifying users - if null, purge them all.
	 * @throws PermissionException
	 *         if the user is not permitted to do this.
	 */
	void purgeInactiveUsers(Integer limit) throws PermissionException;

	/**
	 * Purge the data for the site.
	 * 
	 * @param siteId
	 *        The site id.
	 * @throws PermissionException
	 *         if the user is not permitted.
	 */
	void purgeSite(String siteId) throws PermissionException;

	/**
	 * Purge the data for the site, run immediately.
	 * 
	 * @param siteId
	 *        The site id.
	 * @throws PermissionException
	 *         if the user is not permitted.
	 */
	void purgeSiteNow(String siteId) throws PermissionException;

	/**
	 * Purge the data for the sites in this term.
	 * 
	 * @param termId
	 *        The term id.
	 * @param page
	 *        Which subset of the term to purge.
	 * @param institutionCode
	 *        The institution code (lower case) to limit the action to - ignore if null. if the user is not permitted.
	 * @throws PermissionException
	 *         if the user is not permitted.
	 */
	void purgeTerm(String termId, Subset page, String institutionCode) throws PermissionException;

	/**
	 * Purge the data for the user.
	 * 
	 * @param userId
	 *        The user id.
	 * @throws PermissionException
	 *         if the user is not permitted to do this.
	 */
	void purgeUser(String userId) throws PermissionException;

	/**
	 * Register an archive handler.
	 * 
	 * @param handler
	 *        The archive handler.
	 */
	void registerArchiveHandler(ArchiveHandler handler);

	/**
	 * Register the archiver.
	 * 
	 * @param archiver
	 *        The archiver.
	 */
	void registerArchiver(Archiver archiver);

	/**
	 * Register an import handler.
	 * 
	 * @param applicationId
	 *        The application id of the application this handler imports data for.
	 * @param handler
	 *        The import handler.
	 */
	void registerImportHandler(String applicationId, ImportHandler handler);

	/**
	 * Register a purge handler.
	 * 
	 * @param applicationId
	 *        The application id of the application this handler purges data for.
	 * @param handler
	 *        The purge handler.
	 */
	void registerPurgeHandler(String applicationId, PurgeHandler handler);

	/**
	 * Register a purge user handler.
	 * 
	 * @param applicationId
	 *        The application id of the application this handler purges data for.
	 * @param handler
	 *        The purge user handler.
	 */
	void registerPurgeUserHandler(String applicationId, PurgeUserHandler handler);

	/**
	 * Register the recorder.
	 * 
	 * @param recorder
	 *        The archives recorder.
	 */
	void registerRecorder(ArchivesRecorder recorder);

	/**
	 * Register the Term handler.
	 * 
	 * @param handler
	 *        The Term handler.
	 */
	void registerTermHandler(TermHandler handler);

	/**
	 * Register an Xref handler.
	 * 
	 * @param applicationId
	 *        The application id of the application this handler fixes cross references for.
	 * @param handler
	 *        The Xref handler.
	 */
	void registerXrefHandler(String applicationId, XrefHandler handler);

	/**
	 * Remove a site's embedded media cross-site references
	 * 
	 * @param siteId
	 *        The site id.
	 * @throws PermissionException
	 *         if the user is not permitted.
	 */
	void removeXrefSite(String siteId) throws PermissionException;

	/**
	 * Remove a term's embedded media cross-site references
	 * 
	 * @param termId
	 *        The term id.
	 * @param page
	 *        Which subset of the term to process.
	 * @param institutionCode
	 *        The institution code (lower case) to limit the action to - ignore if null. if the user is not permitted.
	 * @throws PermissionException
	 *         if the user is not permitted.
	 */
	void removeXrefTerm(String termId, Subset page, String institutionCode) throws PermissionException;

	/**
	 * Remove a archive handler registration.
	 * 
	 * @param handler
	 *        The archive handler.
	 */
	void unRegisterArchiveHandler(ArchiveHandler handler);

	/**
	 * Unregister the archiver.
	 * 
	 * @param archiver
	 *        The archiver.
	 */
	void unRegisterArchiver(Archiver archiver);

	/**
	 * Remove a import handler registration.
	 * 
	 * @param applicationId
	 *        The application id of the application this handler imports data for.
	 * @param handler
	 *        The import handler.
	 */
	void unRegisterImportHandler(String applicationId, ImportHandler handler);

	/**
	 * Remove a purge handler registration.
	 * 
	 * @param applicationId
	 *        The application id of the application this handler purges data for.
	 * @param handler
	 *        The purge handler.
	 */
	void unRegisterPurgeHandler(String applicationId, PurgeHandler handler);

	/**
	 * Remove a purge user handler registration.
	 * 
	 * @param applicationId
	 *        The application id of the application this handler purges data for.
	 * @param handler
	 *        The purge user handler.
	 */
	void unRegisterPurgeUserHandler(String applicationId, PurgeUserHandler handler);

	/**
	 * Unregister the recorder.
	 * 
	 * @param recorder
	 *        The archives recorder.
	 */
	void unRegisterRecorder(ArchivesRecorder recorder);

	/**
	 * Unregister the Term handler.
	 * 
	 * @param handler
	 *        The Term handler.
	 */
	void unRegisterTermHandler(TermHandler handler);

	/**
	 * Unregister an Xref handler.
	 * 
	 * @param applicationId
	 *        The application id of the application this handler fixes cross references for.
	 * @param handler
	 *        The Xref handler.
	 */
	void unRegisterXrefHandler(String applicationId, XrefHandler handler);
}
