/*
 * Copyright 2015 Victor Albertos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rx_cache.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.$Gson$Types;
import io.rx_cache.Persistence;
import io.rx_cache.Record;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import javax.inject.Inject;

/**
 * Save objects in disk and delete them too. It uses Gson as json parser.
 */
public final class Disk implements Persistence {
  private final File cacheDirectory;
  private final Gson gson;

  @Inject public Disk(File cacheDirectory) {
    this.cacheDirectory = cacheDirectory;
    gson = new GsonBuilder().excludeFieldsWithModifiers(Modifier.FINAL, Modifier.TRANSIENT,
        Modifier.STATIC).create();
  }

  /**
   * Save in disk the Record passed.
   *
   * @param key the key whereby the Record could be retrieved/deleted later. @see delete and @see
   * retrieve.
   * @param record the record to be persisted.
   */
  @Override public void saveRecord(String key, Record record) {
    save(key, new DiskRecord(record));
  }

  /**
   * Save in disk the object passed.
   *
   * @param key the key whereby the object could be retrieved/deleted later. @see delete and @see
   * retrieve.
   * @param data the object to be persisted.
   */
  public void save(String key, Object data) {
    String wrapperJSONSerialized = gson.toJson(data);
    try {
      File file = new File(cacheDirectory, key);

      FileWriter fileWriter = new FileWriter(file, false);
      fileWriter.write(wrapperJSONSerialized);
      fileWriter.flush();
      fileWriter.close();
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  /**
   * Delete the object previously saved.
   *
   * @param key the key whereby the object could be deleted.
   */
  @Override public void delete(String key) {
    File file = new File(cacheDirectory, key);
    file.delete();
  }

  /**
   * Delete all objects previously saved.
   */
  @Override public void deleteAll() {
    for (File file : cacheDirectory.listFiles()) {
      file.delete();
    }
  }

  /**
   * Retrieve the object previously saved.
   *
   * @param key the key whereby the object could be retrieved.
   * @param clazz the type of class against the object need to be serialized
   */
  public <T> T retrieve(String key, Class<T> clazz) {
    try {
      File file = new File(cacheDirectory, key);
      BufferedReader bufferedReader = new BufferedReader(new FileReader(file.getAbsoluteFile()));

      T data = gson.fromJson(bufferedReader, clazz);

      bufferedReader.close();
      return data;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Retrieve the Record previously saved.
   *
   * @param key the key whereby the object could be retrieved.
   */
  @Override public <T> Record<T> retrieveRecord(String key) {
    try {
      File file = new File(cacheDirectory, key);

      BufferedReader readerTempRecord = new BufferedReader(new FileReader(file.getAbsoluteFile()));
      DiskRecord tempDiskRecord = gson.fromJson(readerTempRecord, DiskRecord.class);
      readerTempRecord.close();

      BufferedReader reader = new BufferedReader(new FileReader(file.getAbsoluteFile()));
      Class classData = Class.forName(tempDiskRecord.getDataClassName());
      Class classCollectionData = tempDiskRecord.getDataCollectionClassName() == null ? Object.class
          : Class.forName(tempDiskRecord.getDataCollectionClassName());

      boolean isCollection = Collection.class.isAssignableFrom(classCollectionData);
      boolean isArray = classCollectionData.isArray();
      boolean isMap = Map.class.isAssignableFrom(classCollectionData);
      DiskRecord<T> diskRecord;

      if (isCollection) {
        Type typeCollection =
            $Gson$Types.newParameterizedTypeWithOwner(null, classCollectionData, classData);
        Type typeRecord =
            $Gson$Types.newParameterizedTypeWithOwner(null, DiskRecord.class, typeCollection,
                classData);
        diskRecord = gson.fromJson(reader, typeRecord);
      } else if (isArray) {
        Type typeRecord =
            $Gson$Types.newParameterizedTypeWithOwner(null, DiskRecord.class, classCollectionData);
        diskRecord = gson.fromJson(reader, typeRecord);
      } else if (isMap) {
        Class classKeyMap = Class.forName(tempDiskRecord.getDataKeyMapClassName());
        Type typeMap =
            $Gson$Types.newParameterizedTypeWithOwner(null, classCollectionData, classKeyMap,
                classData);
        Type typeRecord =
            $Gson$Types.newParameterizedTypeWithOwner(null, DiskRecord.class, typeMap, classData);
        diskRecord = gson.fromJson(reader, typeRecord);
      } else {
        Type type = $Gson$Types.newParameterizedTypeWithOwner(null, DiskRecord.class, classData);
        diskRecord = gson.fromJson(reader, type);
      }

      reader.close();
      return diskRecord;
    } catch (Exception ignore) {
      return null;
    }
  }

  /**
   * Retrieve a collection previously saved.
   *
   * @param key the key whereby the object could be retrieved.
   * @param classCollection type class collection
   * @param classData type class contained by the collection, not the collection itself
   */
  public <C extends Collection<T>, T> C retrieveCollection(String key, Class<C> classCollection,
      Class<T> classData) {
    try {
      File file = new File(cacheDirectory, key);
      BufferedReader reader = new BufferedReader(new FileReader(file.getAbsoluteFile()));

      Type typeCollection =
          $Gson$Types.newParameterizedTypeWithOwner(null, classCollection, classData);
      T data = gson.fromJson(reader, typeCollection);

      reader.close();
      return (C) data;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Retrieve a Map previously saved.
   *
   * @param key the key whereby the object could be retrieved.
   * @param classMap type class Map
   * @param classMapKey type class of the Map key
   * @param classMapValue type class of the Map value
   */
  public <M extends Map<K, V>, K, V> M retrieveMap(String key, Class classMap, Class<K> classMapKey,
      Class<V> classMapValue) {
    try {
      File file = new File(cacheDirectory, key);
      BufferedReader reader = new BufferedReader(new FileReader(file.getAbsoluteFile()));

      Type typeMap =
          $Gson$Types.newParameterizedTypeWithOwner(null, classMap, classMapKey, classMapValue);
      Object data = gson.fromJson(reader, typeMap);

      reader.close();
      return (M) data;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Retrieve an Array previously saved.
   *
   * @param key the key whereby the object could be retrieved.
   * @param classData type class contained by the Array
   */
  public <T> T[] retrieveArray(String key, Class<T> classData) {
    try {
      File file = new File(cacheDirectory, key);
      BufferedReader reader = new BufferedReader(new FileReader(file.getAbsoluteFile()));

      Class<?> clazzArray = Array.newInstance(classData, 1).getClass();
      Object data = gson.fromJson(reader, clazzArray);

      reader.close();
      return (T[]) data;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
}