/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-api/api/src/java/org/etudes/archives/api/Archive.java $
 * $Id: Archive.java 2823 2012-04-03 20:57:39Z ggolden $
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

import java.util.List;
import java.util.Set;

import org.etudes.util.api.Translation;

/**
 * Archive is the collection of archived data for a site.
 */
public interface Archive
{
	/**
	 * Add this artifact to the archive.
	 * 
	 * @param artifact
	 *        The artifact to archive.
	 */
	void archive(Artifact artifact);

	/**
	 * Complete the archive process with the current set of artifacts.
	 */
	void complete();

	/**
	 * Access the artifacts.
	 * 
	 * @return The list of artifacts.
	 */
	List<Artifact> getArtifacts();

	/**
	 * Get all the content references from all the artifacts in the archive.
	 * 
	 * @return The Set of content references.
	 */
	Set<String> getReferences();

	/**
	 * Get the site id that this archive holds.
	 * 
	 * @return the site id.
	 */
	String getSiteId();

	/**
	 * Get the translations for the archive.
	 * 
	 * @return
	 */
	Set<Translation> getTranslations();

	/**
	 * Get ready for artifacts
	 */
	void init();

	/**
	 * Create a new artifact that can be archived.
	 * 
	 * @return A new empty artifact.
	 */
	Artifact newArtifact(String type, String reference);

	/**
	 * Read the archive from storage.
	 */
	void read();

	/**
	 * Read the contents of a file into a byte[]
	 * 
	 * @param name
	 *        The file name.
	 * @param size
	 *        The file size in bytes.
	 * @return The bytes of the file.
	 */
	byte[] readFile(String name, Integer size);
}
