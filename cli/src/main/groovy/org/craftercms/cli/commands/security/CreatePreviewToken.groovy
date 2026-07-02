/*
 * Copyright (C) 2007-2025 Crafter Software Corporation. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.craftercms.cli.commands.security

import org.craftercms.cli.commands.AbstractCommand
import org.craftercms.cli.options.PreviewTokenOptions
import picocli.CommandLine

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@CommandLine.Command(name = 'create-preview-token', description = 'Creates a preview token for the user')
class CreatePreviewToken extends AbstractCommand {

	@CommandLine.Mixin
	PreviewTokenOptions tokenOptions

	@Override
	def run(Object client) {
		createPreviewToken(client, tokenOptions)
	}

	/**
	 * Generates a preview token based on the provided options.
	 * The preview token is created by encrypting a combination of site IDs and an expiration timestamp.
	 *
	 * @param client The HTTP client object used to make API requests.
	 * @param options {@link PreviewTokenOptions} instance containing options for generating the preview token, including siteIds and expiresAt.
	 */
	static def createPreviewToken(client, PreviewTokenOptions options) {
		def siteIds = options.siteIds.split(',')
			.collect { it.trim() }
			.join(',')
		def previewTokenKey = "$siteIds|${textToMilliseconds(options.expiresAt)}".toString()
		def params = [
			siteId    : '',
			text: previewTokenKey
		]
		def path = '/studio/api/2/security/encrypt.json'
		def result = client.post(path, params)

		if (result) {
			println "${result.response.message}. Preview Token: ${result.item}"
		}
	}

	/**
	 * Converts a date string in ISO 8601 format (with time zone) to its equivalent epoch time in milliseconds.
	 *
	 * @param dateString A string representing a date and time in ISO 8601 format (e.g., "2025-01-01T12:30:00Z").
	 * @return The number of milliseconds since the Unix epoch (January 1, 1970, 00:00:00 UTC).
	 */

	static def textToMilliseconds(String dateString) {
		ZonedDateTime parsedDate = ZonedDateTime.parse(dateString, DateTimeFormatter.ISO_ZONED_DATE_TIME)
		return parsedDate.toInstant().toEpochMilli()
	}
}
