#define FUSE_USE_VERSION 26
#include <fuse.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>

static const char *hello_str = "Hello World!\n";
static const char *hello_path = "/hello";

struct my_fs_directory{
	GHashTable* entries;
};

struct my_fs_file{
	guint ref_count;
	gchar* data;
	goffset size;
};

struct my_fs_entry{
	gint type;
	union{
		struct my_fs_directoy* fs_dir;
		struct my_fs_file* fs_file;
	} e;
}

void* my_init(struct fuse_conn_info* conn){
	struct memfs* fs;
	
	fs = my_fs();
	fs->root = my_fs_entry_new(entry_directory);
	
	return fs;
}

int my_fs_create(const char* path, mode_t mode, struct fuse_file_info* fi){
	int ret = -ENOENT;
	struct my_fs_entry* fs_entry;
	char* dirname;
	
	dirname = g_path_get_dirname(path);
	
	if((fs_entry = my_fs_path_get_last_component(dirname)) != NULL){
		if(fs_entry->type == entry_directory){
			my_fs_directory_entry_insert(fs_entry->e.fs_dir, g_path_get_basename(path), my_fs_entry_new(entry_file));
			ret = 0;
		}
	}
	
	g_free(dirname);
	
	return ret;
}

struct my_fs_entry* my_fs_path_get_last_component(const gchar* path){
	struct my_fs* fs = my_fs();
	char** components;
	gint i;
	guint length;
	struct my_fs_entry* fs_entry;
	
	components = g_strsplit(g_path_skip_root(path), G_DIR_SEPARATOR_S, 0);
	length = g_strv_length(components);
	
	fs_entry = fs->root;
	
	for(i=0; i<length; i++){
		struct my_fs_entry* lookup_entry;
		
		if(fs_entry->type != entry_direction){
			goto error;
		}
		
		if((lookup_entry = my_fs_directory_entry_lookup(fs_entry->e.fs_dir, components[i])) == NULL){
			goto error;
		}
		fs_entry = lookup_entry;
	}
	
	g_strfreev(components);
	
	return fs_entry;
error:
	g_strfreev(components);
	return NULL;
}

int my_fs_getattr(const char* path, struct stat* stbuf){
	int ret = -ENOENT;
	struct my_fs_entry* fs_entry;

	if((fs_entry = my_fs_path_get_last_component(path)) != NULL){
		stbuf->st_mode = S_IRUSER | S_IWUSR | S_IRGRP | S_IROTH;
		stbuf->st_nlink = 1;
		stbuf->st_uid = getuid();
		stbuf->st_gid = getgid();
		stbuf->st_atime = stbuf->st_mtime = stbuf->st_ctime = 0;

		switch(fs_entry->type){
			case entry_directory:
				stbuf->st_mode |= S_IFDIR | S_IXUSR | S_IXGRP | S_IXOTH;
				stbuf->st_size = my_fs_directory_size(fs_entry->e.fs_dir);
				ret = 0;
				break;
			case entry_file:
				stbuf->st_mode |= S_IFREG;
				stbuf->st_size = fs_entry->e.fs_file->size;

				ret = 0;
				break;
		}
	}

	return ret;
}


struct fuse_operations memfs_oper = {
	.getattr	= mt_fs_getattr,
	.create		= my_fs_create,
	.init		= my_init,
};

int main(int argc, char* argv[]) {
	return fuse_main(argc, argv, &memfs_oper, NULL);
}
