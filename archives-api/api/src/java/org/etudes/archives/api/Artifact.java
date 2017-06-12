/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-api/api/src/java/org/etudes/archives/api/Artifact.java $
 * $Id: Artifact.java 2823 2012-04-03 20:57:39Z ggolden $
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

package org.etudes.archives.api;

import java.util.Map;
import java.util.Set;

/**
 * Artifact represents one unit of information in an archives.
 */
public interface Artifact
{
	/**
	 * Get the archives file name for this artifact (relative to the archive)
	 * 
	 * @return The relative file name.
	 */
	String getFileName();

	/**
	 * Access the artifact id.
	 * 
	 * @return The artifact id.
	 */
	Long getId();

	/**
	 * Generate another unique suffix for files stored with this artifact.
	 * 
	 * @return The file suffix.
	 */
	String getNextFileSuffix();

	/**
	 * Access the Map of properties that describe this artifact.
	 * 
	 * @return The Map of properties.
	 */
	Map<String, Object> getProperties();

	/**
	 * Get the reference string for this artifact (from the source system).
	 * 
	 * @return The artifact's reference string.
	 */
	String getReference();

	/**
	 * Access the set of reference strings to artifacts referenced by this artifact.
	 * 
	 * @return The set of reference strings.
	 */
	Set<String> getReferences();

	/**
	 * Get the artifact type.
	 * 
	 * @return The artifact type
	 */
	String getType();

	/**
	 * Set the artifact's reference string (from the source system).
	 * 
	 * @param reference
	 *        The artifact's reference string.
	 */
	void setReference(String reference);

	/**
	 * Set the artifact type.
	 * 
	 * @param type
	 *        The artifact type.
	 */
	void setType(String type);

}
