/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-api/api/src/java/org/etudes/archives/api/ArchivesRecorder.java $
 * $Id: ArchivesRecorder.java 3048 2012-06-26 03:37:23Z ggolden $
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

package org.etudes.archives.api;

import java.util.List;

/**
 * ArchivesRecorder handles recording archiving information.
 */
public interface ArchivesRecorder
{
	/**
	 * Get all the archive descriptions for sites in the given term, possibly filtered to include only those with site titles matching the institutionCode prefix.
	 * 
	 * @param termId
	 *        The term id string (i.e. w10)
	 * @param page
	 *        Which subset of the archives to access.
	 * @param institutionCode
	 *        The site id prefix for a particular client (i.e "fh"), or null to include all in the term.
	 * @return The list of matching ArchiveDescriptions.
	 */
	List<ArchiveDescription> getTermArchives(String termId, Subset page, String institutionCode);

	/**
	 * Find all the archives that this user has access to. Sort by term and then site title.
	 * 
	 * @param userId
	 *        The user id.
	 * @return A List of ArchiveDescriptions for each archive the user has access to, possibly empty.
	 */
	List<ArchiveDescription> getUserArchives(String userId);

	/**
	 * Record that this site is being archived.
	 * 
	 * @param siteId
	 *        The site id.
	 */
	void recordArchive(String siteId);

	/**
	 * Remove the record of a site as being archived.
	 * 
	 * @param siteId
	 *        The site id.
	 */
	void recordArchiveDelete(String siteId);

	/**
	 * Record that this site is being purged.
	 * 
	 * @param siteId
	 *        The site id.
	 */
	void recordPurge(String siteId);
}
