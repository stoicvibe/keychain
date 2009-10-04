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
import danger.ui.*;
import danger.audio.*;
import danger.util.*;
import danger.util.DEBUG;
import danger.crypto.*;
import java.util.Random;
import java.util.Vector;

public class KeyList extends ScreenWindow implements Resources, Commands
{
	keychain					app;
	Random					rand;

	SettingsDB				settings = new SettingsDB("settings", true);

	Rect						view;
	ListView					keys							= null;
	Button					addButton					= null;
	Menu						menu							= null;
	Menu						timeoutMenu					= null;

	DialogWindow			addDialog					= null;
	TextField				descriptionField			= null;
	TextField				keyField						= null;
	EditText					noteField					= null;

	TextField				newPasswordField1			= null;
	TextField				newPasswordField2			= null;

	int						remove						= -1;

	static final int		MENU_BEGIN					= 1;
	static final int		MENU_KEY_DETAILS			= 2;
	static final int		MENU_ADD_KEY				= 3;
	static final int		MENU_ADD_KEY_DONE			= 4;
	static final int		MENU_DISCARD_KEY			= 5;
	static final int		MENU_RANDOM_VALUE			= 6;
	static final int		MENU_CANCEL					= 7;
	static final int		MENU_DO_NOTHING			= 8;
	static final int		MENU_CHANGE_PASS			= 9;
	static final int		MENU_CHANGE_PASS_DONE	= 10;
	static final int		MENU_TIMEOUT				= 11;
	static final int		MENU_END						= 12;

	private long			lastTime						= System.currentTimeMillis();
	private long			seed							= lastTime;

	static final char[]	VALID_KEYS					= { 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' };

	public KeyList(keychain app) {
		MenuItem			timeoutItem;
		int				i;
		int				timeout;

		this.app = app;

		/* Do any needed datastore format upgrades - We are on V1 now */
		upgradeV0toV1.upgrade(this, app);

		try {
			timeout = settings.getIntValue("timeout");
		} catch (SettingsDBException e) {
			timeout = 30;
		}

		view = getBounds();

		rand = new Random();

		setTitle("Password List");

		/* Create the menu */
		menu = getActionMenu();
		menu.removeAllItems();

		menu.addItem("Add Password", MENU_ADD_KEY, 0, null, this).setShortcut('n', true);
		menu.addItem("Discard Password", MENU_DISCARD_KEY, 0, null, this);
		menu.addItem("Change Master Password", MENU_CHANGE_PASS, 0, null, this);
		timeoutItem = menu.addItem("Timeout");

		timeoutMenu = new Menu("Timeout");
		timeoutMenu.addItem("Never", MENU_TIMEOUT, -1, null, this);
		timeoutMenu.addItem("30 Seconds", MENU_TIMEOUT, 30, null, this);
		timeoutMenu.addItem("1 Minute", MENU_TIMEOUT, 60, null, this);
		timeoutMenu.addItem("3 Minutes", MENU_TIMEOUT, 60 * 3, null, this).setChecked(true);
		timeoutMenu.addItem("5 Minutes", MENU_TIMEOUT, 60 * 5, null, this);
		timeoutMenu.addItem("10 Minutes", MENU_TIMEOUT, 60 * 10, null, this);

		timeoutItem.addSubMenu(timeoutMenu);

		for (i = 0; i < timeoutMenu.itemCount(); i++) {
			MenuItem		current = timeoutMenu.getItem(i);
			Event			w = timeoutMenu.getItem(i).getEvent();

			current.setChecked(w.data == timeout);
		}

		/* Create the list view */
		keys = new ListView();
		refreshKeyList();

		addButton = new Button("Add Password");

		keys.setWidth(view.getWidth());
		keys.setHeight(view.getHeight() - addButton.getHeight() - 5);
		keys.snapHeight();

		keys.setTop(0);
		keys.setLeft(0);

		addChild(keys);
		keys.show();

		Layout.positionBelow(addButton, keys, 5);
		Layout.centerHorizontal(addButton, this);
		addButton.setEvent(new Event(this, MENU_ADD_KEY));

		addChild(addButton);
		addButton.show();

		/* Finish up */
		setFocusedChild(keys);
	}

	public void showPassDialog(String title, String description, String pass, String note) {
		StaticText				descriptionText	= null;
		StaticText				keyText				= null;
		StaticText				noteText				= null;

		addDialog = new DialogWindow(title);

		addDialog.setHideOnButtonEvent(false);
		addDialog.setShowCancel(false);

		addDialog.addTopFrameButton("Cancel", MENU_CANCEL, 0, null, (char) 0);
		addDialog.addBottomFrameButton("Done", MENU_ADD_KEY_DONE, 0, null, Shortcut.BACK_BUTTON);
		addDialog.addBottomFrameButton("Random Password", MENU_RANDOM_VALUE, 0, null, (char) 0);

		descriptionText = new StaticText("Description:");
		descriptionText.setTop(0);
		descriptionText.setLeft(0);

		addDialog.addChild(descriptionText);
		descriptionText.show();


		descriptionField = new TextField(true, true);
		descriptionField.setWidth(200);
		Layout.positionBelow(descriptionField, descriptionText, 2);

		if (description != null) {
			descriptionField.setText(description);
		}

		addDialog.addChild(descriptionField);
		descriptionField.show();


		keyText = new StaticText("Password:");
		Layout.positionBelow(keyText, descriptionField, 5);

		addDialog.addChild(keyText);
		keyText.show();


		keyField = new TextField(false, false);
		keyField.setWidth(200);
		Layout.positionBelow(keyField, keyText, 2);

		if (pass != null) {
			keyField.setText(pass);
		}

		addDialog.addChild(keyField);
		keyField.show();


		noteText = new StaticText("Notes:");
		Layout.positionBelow(noteText, keyField, 5);

		addDialog.addChild(noteText);
		noteText.show();


		noteField = new EditText();
		noteField.setSize(200, 35);
		Layout.positionBelow(noteField, noteText, 2);

		if (note != null) {
			noteField.setText(note);
		}

		if (description == null) {
			addDialog.setFocusedChild(descriptionField);
		}

		addDialog.addChild(noteField);
		noteField.show();

		addDialog.setListener(this);
		addDialog.setAutoSize(true);
		addDialog.show();
	}

	public void refreshKeyList() {
		int			i;

		keys.removeAllItems();

		for (i = 0; i < app.storedData.getRecordCount(); i++) {
			String		name = getName(i);

			if (name != null) {
				keys.addItem(name, this, MENU_KEY_DETAILS, i, null);
			}
		}

		keys.setComparator(ListView.CASE_INSENSITIVE_COMPARATOR);
		keys.setHasScrollbar(true);
		keys.sort();
		invalidate();
	}

	private void resetPassword(String newPass) {
		DataStore		passwordData	= DataStore.createDataStore("passwordData", true, true);
		MD5				md5				= new MD5();
		Cipher			cipher			= Cipher.getInstance("blowfish");
		String[]			names				= new String[app.storedData.getRecordCount()];
		String[]			values			= new String[app.storedData.getRecordCount()];
		String[]			notes				= new String[app.storedData.getRecordCount()];
		int				i;

		/* Keep all the data in memory, in the unencrypted form */
		for (i = 0; i < app.storedData.getRecordCount(); i++) {
			names[i]		= getName(i);
			values[i]	= getPass(i);
			notes[i]		= getNote(i);
		}

		/* Remove all the old data */
		passwordData.removeAllRecords();
		app.storedData.removeAllRecords();

		/* Create the new password */
		md5.update(newPass.getBytes());
		app.pass = md5.digest();
		md5 = null;

		if (cipher != null && app.pass != null) {
			int			bs;
			int			s;
			byte[]		data;
			byte[]		test = LoginWindow.PASSWORD_TEST_DATA.getBytes();
			String		encrypted;

			cipher.init(app.pass, null);

			/*
				The size of data sent into and out of encrypt/decrypt must be a multiple of
				the block size.  Ugg thats annoying.
			*/
			bs = cipher.getBlockSize();

			s = test.length + (bs - (test.length % bs));
			data = new byte[s];

			for (i = 0; i < test.length; i++) {
				data[i] = test[i];
			}

			encrypted = new String(cipher.encrypt(data));
			passwordData.addRecord(encrypted.getBytes());
		}

		/* Store all the new values */
		for (i = 0; i < names.length; i++) {
			storeKey(names[i], values[i], notes[i]);
		}
	}

	public boolean storeKey(String description, String pass, String note) {
		if (note == null) {
			note = "";
		}

		if (pass == null) {
			pass = "";
		}

		if (description != null && description.length() > 0) {
			byte[]		descriptionData = encrypt(description);
			byte[]		passData = encrypt(pass);
			byte[]		noteData = encrypt(note);
			byte[]		result = new byte[descriptionData.length + 1 + passData.length + 1 + noteData.length];
			int			i;
			int			s = 0;

			if (descriptionData.length > 255 || passData.length > 255) {
				return(false);
			}

			result[s++] = (byte) descriptionData.length;
			for (i = 0; i < descriptionData.length; i++) {
				result[s++] = descriptionData[i];
			}

			result[s++] = (byte) passData.length;
			for (i = 0; i < passData.length; i++) {
				result[s++] = passData[i];
			}

			for (i = 0; i < noteData.length; i++) {
				result[s++] = noteData[i];
			}

			app.storedData.addRecord(result);
			return(true);
		}

		return(false);
	}

	/*
		The format is as follows:
		1 byte				name length
		x bytes				name data
		1 byte				password length
		x bytes				password data
		remaning bytes		note data
	*/
	private String getName(int i) {
		byte[]		data = app.storedData.getRecordData(i);
		byte			s = data[0];
		byte[]		result = new byte[s];

		for (i = 0; i < result.length; i++) {
			result[i] = data[i + 1];
		}

		return(decrypt(result));
	}

	private String getPass(int i) {
		byte[]		data = app.storedData.getRecordData(i);
		byte			s = data[0];
		byte			result[] = new byte[data[++s]];

		for (i = 0; i < result.length; i++) {
			result[i] = data[++s];
		}

		return(decrypt(result));
	}

	private String getNote(int i) {
		byte[]		data = app.storedData.getRecordData(i);
		byte			s = data[0];
		byte			result[];

		s += data[++s];

		/* s now points to the first char of the value data */
		result = new byte[data.length - (++s) - 1];

		for (i = 0; i < result.length; i++) {
			result[i] = data[++s];
		}

		return(decrypt(result));
	}

	private byte[] encrypt(String string) {
		Cipher		cipher	= Cipher.getInstance("blowfish");

		if (string != null && cipher != null && app.pass != null) {
			byte[]		value 	= string.getBytes();
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

			cipher.init(app.pass, null);
			return(cipher.encrypt(value));
		}

		return(null);
	}

	private String decrypt(byte[] value) {
		Cipher		cipher	= Cipher.getInstance("blowfish");

		if (value != null && cipher != null && app.pass != null) {
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

			cipher.init(app.pass, null);
			value = cipher.decrypt(value);

			/* The decrypt call will padd the end with zeros */
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

	public final boolean receiveEvent(Event what) {
		MenuItem			selectedMenuItem = keys.getSelectedItem();
		int				selected = -1;
		int				selectedIndex = getListViewSelectedItem(keys);

		if (what.type > MENU_BEGIN && what.type < MENU_END) {
			/* Update the time and seed based on user input */

			updateSeed();
		}

		/*
			If the event is fired from a list entry then it will have the
			selected item as its data.  If that isn't the case though we
			still want to act on that item, so we will pull the selected
			item, by getting the selected menu item, and getting the
			information from its event.
		*/

		if (selectedMenuItem != null) {
			Event		selectedEvent = selectedMenuItem.getEvent();

			selected = selectedEvent.data;
		} else {
			selected = what.data;
		}

		switch (what.type) {
			case Event.EVENT_PERIODIC_PULSE: {
				int		timeout;

				/* Check to see if we ahve timed out, and lave if we have */
				try {
					timeout = settings.getIntValue("timeout");
				} catch (SettingsDBException e) {
					timeout = 30;
				}

				if (timeout > 0 && (System.currentTimeMillis() - lastTime) > (timeout * 1000)) {
					app.returnToLauncher();
				}

				break;
			}

			case MENU_TIMEOUT: {
				int			i;

				settings.setIntValue("timeout", what.data);

				for (i = 0; i < timeoutMenu.itemCount(); i++) {
					MenuItem		current = timeoutMenu.getItem(i);
					Event			w = timeoutMenu.getItem(i).getEvent();

					current.setChecked(w.data == what.data);
				}

				break;
			}

			case MENU_ADD_KEY: {
				remove = -1;
				showPassDialog("Add Password", null, null, null);

				break;
			}

			case MENU_ADD_KEY_DONE: {
				if (descriptionField.toString().length() > 0) {
					addDialog.hide();

					if (remove != -1) {
						/* If they are editing another one, remove it first */
						app.storedData.removeRecord(remove);
					}

					storeKey(descriptionField.toString(), keyField.toString(), noteField.toString());
					refreshKeyList();
				} else {
					new AlertWindow("A description is required").show();
				}

				break;
			}

			case MENU_CANCEL: {
				if (addDialog != null) {
					addDialog.hide();
				}

				break;
			}

			case MENU_KEY_DETAILS: {
				remove = selected;
				showPassDialog("Password Details", getName(selected), getPass(selected), getNote(selected));

				break;
			}

			case MENU_DISCARD_KEY: {
				app.storedData.removeRecord(selected);
				refreshKeyList();

				break;
			}

			case MENU_RANDOM_VALUE: {
				if (keyField != null) {
					int				i;
					char[]			password;
					int				size = getRand(5, 15);

					password = new char[size];
					for (i = 0; i < size; i++) {
						password[i] = VALID_KEYS[getRand(0, VALID_KEYS.length - 1)];
					}

					keyField.setText(new String(password));
					invalidate();
				}

				break;
			}

			case MENU_CHANGE_PASS: {
				DialogWindow			dialog		= new DialogWindow("Change Password");
				StaticText				passText		= null;
				StaticText				repeatText	= null;

				dialog.addBottomFrameButton("Done", MENU_CHANGE_PASS_DONE, 0, null, Shortcut.BACK_BUTTON);

				passText = new StaticText("New password:");
				passText.setTop(0);
				passText.setLeft(0);

				dialog.addChild(passText);
				passText.show();

				newPasswordField1 = new PasswordTextField();
				newPasswordField1.setWidth(200);
				Layout.positionBelow(newPasswordField1, passText, 2);

				dialog.addChild(newPasswordField1);
				newPasswordField1.show();


				repeatText = new StaticText("Repeat password:");
				Layout.positionBelow(repeatText, newPasswordField1, 5);

				dialog.addChild(repeatText);
				repeatText.show();

				newPasswordField2 = new PasswordTextField();
				newPasswordField2.setWidth(200);
				Layout.positionBelow(newPasswordField2, repeatText, 2);

				dialog.addChild(newPasswordField2);
				newPasswordField2.show();

				dialog.setListener(this);
				dialog.setAutoSize(true);
				dialog.show();

				break;
			}

			case MENU_CHANGE_PASS_DONE: {
				if (newPasswordField1.toString().length() > 7) {
					if (newPasswordField1.toString().equals(newPasswordField2.toString())) {
						resetPassword(newPasswordField1.toString());
						refreshKeyList();
					} else {
						new AlertWindow("Passwords do not match").show();
					}
				} else {
					new AlertWindow("Password is too short").show();
				}

				break;
			}
		}

		/* Ensure that the proper item is selected */
		if (selectedIndex < keys.itemCount() && selectedIndex > -1) {
			keys.setSelection(selectedIndex);
		} else {
			keys.setSelection(keys.itemCount() - 1);
		}

		return(super.receiveEvent(what));
	}

	public boolean eventKeyUp(char c, Event inEvent) {
		updateSeed();

		switch (c) {
			case '\b': {
				receiveEvent(new Event(this, MENU_DISCARD_KEY));

				break;
			}

			case 'N':
			case 'n': {
				receiveEvent(new Event(this, MENU_ADD_KEY));

				break;
			}

			default: {
				return(super.eventKeyUp(c, inEvent));
			}
		}

		return(true);
	}

	/* Update the seed based on the time between calls to updateSeed.  */
	private void updateSeed() {
		long			time = System.currentTimeMillis();
		byte			value = (byte) ((time - lastTime) % 255);

		seed = seed << 8;
		seed |= value;

		lastTime = time;
		rand.setSeed(seed);
	}

	public int getRand(int min, int max) {
		return(rand.nextInt(max - min + 1) + min);
	}

	public boolean eventWidgetUp(int inWhichWidget, Event inEvent) {
		updateSeed();

		switch (inWhichWidget) {
			case Event.DEVICE_BUTTON_BACK: {
				app.returnToLauncher();

				break;
			}

			default: {
				return(super.eventWidgetUp(inWhichWidget, inEvent));
			}
		}

		return(true);
	}

	/*
		helper function for ListView

		ListView does not have a way to get the index of the selected item
	*/
	public int getListViewSelectedItem(ListView list) {
		MenuItem			item = list.getSelectedItem();
		int				i;

		for (i = 0; i < list.itemCount(); i++) {
			MenuItem			current = list.getItem(i);

			if (current.equals(item)) {
				return(i);
			}
		}

		return(-1);
	}
}
