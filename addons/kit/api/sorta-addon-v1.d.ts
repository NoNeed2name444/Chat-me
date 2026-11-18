/**
 * The Sorta add-on API.
 * @version 1.0
 *
 * Calls that read or change ideas return a Promise. sorta.has() and the
 * three on() calls that register handlers return at once. Everything that
 * goes in or out is plain JSON. A call that needs a permission the add-on
 * doesn't hold fails with a PermissionError.
 *
 * v1.json is the machine copy of this file, made by
 * tools/api_snapshot.py. A later 1.x version only adds to it.
 */

/** What an add-on can ask for in manifest.json. "ai" is reserved for the AI work. */
type Permission = "ideas.read" | "ideas.read.folder" | "ideas.write" | "commands" | "cards" | "board.palette" | "net" | "ai";

/** Any JSON value. */
type Json = null | boolean | number | string | Json[] | Record<string, Json>;

/** A setting's value: true or false for a toggle, text for a text field or a choice. */
type SettingValue = boolean | string;

/** The add-on's settings, by key, as settings.json declares them. */
type Settings = Record<string, SettingValue>;

interface ApiVersion {
  major: 1;
  minor: number;
}

/** One idea, as the add-on sees it. Times are ISO 8601 in UTC, like 2026-11-15T09:30:00Z. */
interface Idea {
  id: string;
  kind: "idea" | "page";
  title: string;
  text: string;
  tags: string[];
  /** The ids of the ideas this one links to. */
  links: string[];
  /** The id of the idea's folder, or null while it waits in To sort. */
  folder: string | null;
  createdAt: string;
  updatedAt: string;
}

interface Folder {
  id: string;
  name: string;
  /** The folder it sits in, or null at the top. */
  parent: string | null;
  /** The folder's own words, at most five. */
  words: string[];
}

interface ListOptions {
  /** Only the ideas in this folder. null means the ideas in To sort. */
  folder?: string | null;
  kind?: "idea" | "page";
  /** How many ideas to return: 50 unless you say, and at most 200. @max 200 */
  limit?: number;
  /** The cursor from the page before, to get the next page. */
  cursor?: string;
}

interface IdeaPage {
  ideas: Idea[];
  /** Pass it to list() for the next page. null on the last page. */
  cursor: string | null;
}

interface SearchOptions {
  /** 20 unless you say, and at most 50. @max 50 */
  limit?: number;
}

/** A new idea. It lands in To sort. */
interface NewIdea {
  title?: string;
  text: string;
}

interface Captured {
  id: string;
}

/** What a command gets: the ids of the ideas the person had chosen, if any. */
interface CommandEvent {
  ideas: string[];
}

/** What a command may return. Sorta shows the message quietly. */
interface CommandReply {
  /** @maxLength 120 */
  message?: string;
}

/** What a card view returns for one idea. Sorta draws the lines in its own type and colours. */
interface CardLines {
  /** At most 3 lines of at most 80 characters each. @max 3 @maxLength 80 */
  lines: string[];
}

interface FetchOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  headers?: Record<string, string>;
  body?: string;
}

interface FetchReply {
  status: number;
  /** The address the reply came from, after redirects within the add-on's hosts. */
  url: string;
  headers: Record<string, string>;
  body: string;
}

/** Every error a call gives carries a code, the same on every phone. */
declare class SortaError extends Error {
  readonly code: string;
}

/** The add-on asked for something it doesn't hold. */
declare class PermissionError extends SortaError {
  readonly permission: Permission;
}

/** One call made more than 100 writes or 20 captures, so none of them were applied. */
declare class WriteLimitError extends SortaError {
}

declare namespace sorta {
  /** The API's version: major 1, minor 0 for now. */
  const api: ApiVersion;

  /**
   * Whether a permission is granted, like has("net"), or whether this
   * Sorta has a call, like has("ideas.capture").
   */
  function has(name: string): boolean;

  namespace ideas {
    /** @permission ideas.read ideas.read.folder */
    function list(options?: ListOptions): Promise<IdeaPage>;
    /** null if the idea is gone or out of the add-on's reach. @permission ideas.read ideas.read.folder */
    function get(id: string): Promise<Idea | null>;
    /** Uses Sorta's own search. @permission ideas.read ideas.read.folder */
    function search(text: string, options?: SearchOptions): Promise<Idea[]>;
    /** @permission ideas.write */
    function capture(idea: NewIdea): Promise<Captured>;
    /** @permission ideas.write */
    function tag(id: string, tags: string[]): Promise<void>;
    /** @permission ideas.write */
    function untag(id: string, tags: string[]): Promise<void>;
    /** @permission ideas.write */
    function link(a: string, b: string): Promise<void>;
    /** @permission ideas.write */
    function unlink(a: string, b: string): Promise<void>;
    /** Moves an idea into a folder, or back to To sort with null. @permission ideas.write */
    function file(id: string, folder: string | null): Promise<void>;
  }

  namespace folders {
    /** @permission ideas.read ideas.read.folder */
    function list(): Promise<Folder[]>;
  }

  namespace commands {
    /** The id is one from contributes.commands in manifest.json. @permission commands */
    function on(id: string, handler: (event: CommandEvent) => CommandReply | void | Promise<CommandReply | void>): void;
  }

  namespace cards {
    /** @permission cards */
    function on(handler: (idea: Idea) => CardLines | Promise<CardLines>): void;
  }

  namespace settings {
    function get(): Promise<Settings>;
    /** Runs after the person changes a setting. */
    function on(handler: (settings: Settings) => void | Promise<void>): void;
  }

  /** The add-on's own storage, at most 256 KB in all. */
  namespace storage {
    /** null if nothing is stored under the key. */
    function get(key: string): Promise<Json | null>;
    function set(key: string, value: Json): Promise<void>;
    function remove(key: string): Promise<void>;
  }

  namespace net {
    /** HTTPS to the add-on's granted hosts only, at most 1 MB each way and 10 s. @permission net */
    function fetch(url: string, options?: FetchOptions): Promise<FetchReply>;
  }
}

/** Lines go to the add-on's own log on the phone, which keeps the last 200. */
declare namespace console {
  function log(...values: Json[]): void;
}
