/*
 * Copyright (C) 2003-2004 Micah N. Gorrell
 * ineedsleep@myrealbox.com
 * 172 N 700 W
 * Spanish Fork, UT 84660
 * 
 * LICENSE
 * =======
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of version 2 of the GNU General Public License as
 * published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of       
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License    
 * along with this program; if not, write to the Free Software          
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA. 
 */

package com.frozenbutterfly.keychain;

import danger.app.*;
import danger.util.*;
import danger.util.DEBUG;
import danger.crypto.*;
import java.util.Random;

public class upgradeV0toV1
{
	public static void upgrade(KeyList keylist, keychain app) {
		int			i;
		DataStore	storedData = DataStore.createDataStore("keychain", true, true);

if (storedData.getRecordCount() > 0) {
	DEBUG.p("[KEYCHAIN] You have " + storedData.getRecordCount() + " records waiting to be upgraded");
} else {
	DEBUG.p("[KEYCHAIN] No data to upgrade");
}

		for (i = 0; i < storedData.getRecordCount(); i++) {
			byte[]		data = storedData.getRecordData(i);

DEBUG.p("[KEYCHAIN] Upgrading item: " + getName(data, app.pass));
			keylist.storeKey(getName(data, app.pass), getPass(data, app.pass), getNote(data, app.pass));
		}

		storedData.removeAllRecords();
	}

	/*
		the format is as follows:
		1 byte				name length
		x bytes				name data
		1 byte				password length
		x bytes				password data
		remaning bytes		note data
	*/
	private static String getName(byte data[], byte[] pass) {
		int			c;

		for (c = 0; c < data.length; c++) {
			if (data[c] == 0) {
				byte[]			result = new byte[c];

				for (c = 0; c < result.length; c++) {
					result[c] = data[c];
				}

				return(decrypt(result, pass));
			}
		}

		return(null);
	}

	private static String getPass(byte data[], byte[] pass) {
		int			b = -1;
		int			e = -1;
		int			c;

		for (c = 0; c < data.length; c++) {
			if (data[c] == 0) {
				if (b == -1) {
					/* Begining */
					b = c + 1;
				} else if (e == -1) {
					/* Ending */
					e = c;
				}
			}
		}

		if (e == -1) {
			e = data.length;
		}

		if (e != -1 && b != -1 && (e - b) > 0) {
			byte[]		result = new byte[e - b];
			int			i = 0;

			for (c = b; c < e; c++) {
				result[i++] = data[c];
			}

			return(decrypt(result, pass));
		}

		return(null);
	}

	private static String getNote(byte data[], byte[] pass) {
		byte[]		result = null;
		int			count = 0;
		int			c;
		int			i = 0;

		for (c = 0; c < data.length; c++) {
			if (count > 1) {
				if (result == null) {
					result = new byte[data.length - c];
				}

				result[i++] = data[c];
			} else if (data[c] == 0) {
				count++;
			}
		}

		if (result != null) {
			return(decrypt(result, pass));
		} else {
			return(null);
		}
	}

	private static String decrypt(byte[] value, byte[] pass) {
		Cipher		cipher	= Cipher.getInstance("blowfish");

		if (value != null && cipher != null && pass != null) {
			int			bs	= cipher.getBlockSize();

			if (value.length % bs != 0) {
				int			s	= value.length + (bs - (value.length % bs));
				int			i;
				byte[]		data;

				data = new byte[s];
				for (i = 0; i < value.length; i++) {
					data[i] = value[i];
				}

				value = data;
			}

			cipher.init(pass, null);
			value = cipher.decrypt(value);

			/* the decrypt call will padd the end with zeros */
			if (value != null) {
				int			i = 0;
				byte[]		result;

				while (i < value.length && value[i] != 0) {
					i++;
				}

				result = new byte[i];
				for (i = 0; i < result.length; i++) {
					result[i] = value[i];
				}

				return(new String(result));
			}
		}

		return(null);
	}
}
