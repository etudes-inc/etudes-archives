/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-api/api/src/java/org/etudes/archives/api/TermHandler.java $
 * $Id: TermHandler.java 3005 2012-06-19 04:03:21Z ggolden $
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
 * TermHandler handles mapping site ids to terms.
 */
public interface TermHandler
{
	/**
	 * Get some of the site ids for the term. Consider all sites, even those that may have already been deleted.
	 * 
	 * @param termId
	 *        The term id.
	 * @param page
	 *        Which subset of the term to access.
	 * @param institutionCode
	 *        The institution code (lower case) to limit the action to - ignore if null. if the user is not permitted.
	 * @return The List of site ids, possibly empty, for the term.
	 */
	List<String> getAllTermSiteIds(String termId, Subset page, String institutionCode);

	/**
	 * Access the site term id for this site id. Return the "unknown" (=1) value if we don't know.
	 * 
	 * @param siteId
	 *        The site id.
	 * @return The site term id for this site.
	 */
	Long getSiteTerm(String siteId);

	/**
	 * Get some of the site ids for the term. Only consider those that still exist (have not been purged).
	 * 
	 * @param termId
	 *        The term id.
	 * @param page
	 *        Which subset of the term to access.
	 * @param institutionCode
	 *        The institution code (lower case) to limit the action to - ignore if null. if the user is not permitted.
	 * @return The List of site ids, possibly empty, for the term.
	 */
	List<String> getTermSiteIds(String termId, Subset page, String institutionCode);

	/**
	 * Establish or update the site term entry for this site.
	 * 
	 * @param siteId
	 *        The site id.
	 */
	void setSiteTerm(String siteId);
}
