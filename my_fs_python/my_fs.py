import errno
import fuse
import stat
import time
import sys
import os.path
# from fuse import FUSE

fuse.fuse_python_api = (0, 2)

class MyStat(fuse.Stat):
    def __init__(self, is_dir, size):
        fuse.Stat.__init__(self)
        if is_dir:
            self.st_mode = stat.S_IFDIR | 0555
            self.st_nlink = 2
        else:
            self.st_mode = stat.S_IFREG | 0777
            self.st_nlink = 1
            self.st_size = size
        self.st_atime = int(time.time())
        self.st_mtime = self.st_atime
        self.st_ctime = self.st_atime
    
class FSEntry():
    """
    All paths in FSEntry are absolute paths
    """
    def __init__(self, is_dir, size, data, location, parent):
        self.parent = parent
        self.is_dir = is_dir
        self.data = data
        self.location = location
        self.size = size
        if is_dir:
            self.children = {}
        else:
            self.children = None
        self.stat = MyStat(is_dir, size)
            
    def add_sub_directoy(self, path):
        newDir = FSEntry(True, None, None, path, self)
        self.children[path] = newDir
        return newDir
    
    def add_file(self, path, data, size):
        newFile = FSEntry(False, size, data, path, self)
        self.children[path] = newFile
        return newFile
    
    def remove_file(self, path):
        if self.children is None:
            return -errno.ENOENT
        if path in self.children:
            del self.children[path]
            return 0
        else:
            return -errno.ENOENT

class my_fs(fuse.Fuse):
    def __init__(self, *args, **kw):
        fuse.Fuse.__init__(self, *args, **kw)
#         fuse.Fu
        #TODO: get root path and put into root directory FS entry from fuse args
#         print 'root dir is: ' + sys.argv[1]
        self.root_path = os.path.abspath(sys.argv[1])
        self.root = FSEntry(True, None, None, '/', None)
        
    def getattr(self, path):
#         print 'getting attributes for ' + path
        entry = self.findentry(path)
        if entry is None:
#             print 'could not find entry for: ' + path
            return -errno.ENOENT
        else:
#             print entry.stat
            return entry.stat
            
#         st = fuse.Stat();
#         st.st_mode = stat.S_IFDIR | 0755 
#         st.st_nlink = 2
#         st.st_atime = int(time.time())
#         st.st_mtime = st.st_atime
#         st.st_ctime = st.st_atime
#         
#         if path == '/':
#             pass
#         else:
#             return - errno.ENOENT
#         return st

    def is_sub_dir(self, root_path, sub_dir_path):
#         print 'is_sub_dir function------------------------------'
#         print 'root_path: '+root_path
#         print 'sub_dir_path: '+sub_dir_path
        commonPrefix = os.path.commonprefix([root_path, sub_dir_path])
#         print 'common prefix: ' + commonPrefix
        """
        check that the common prefix is the root AND that the sub dir path is not
        the same as the root -> otherwise is_sub_dir("/a/b/", "/a/b/) would 
        return true
        """ 
#         print '------------------------'
        return (commonPrefix == root_path) and not (commonPrefix == sub_dir_path)
    
    def findentry(self, path):
        """
        Checks if a path is a subdirectory of the root, and then searches
        the tree until the target path's folder is the same as the current
        folder. If nothing can be found, then returns None. Returns the
        FSEntry for the directory if the directory is a directory, or 
        the FSEntry of the file in the directory if it is a a file.
        Input path must be absolute.
        
        Is the essential function of getattr(). Check here if problems
        are encountered.
        """
        abspath = os.path.abspath(path)    
#         print abspath + ' in fs?'
        """
        Check if the path is inside the file system- the path either is the root
        directory, is a subdirectory of the root directory, or is the root directory
        """
        in_fs = self.is_sub_dir(self.root.location, abspath) or path == self.root.location
#         print str(in_fs)
        if in_fs:
            currentDir = self.root.location
            targetDir = os.path.dirname(path)
#             print 'target dir for: ' + path + ' is: '+ targetDir
#             print 'path is: ' + path
#             print 'root location: '+ self.root.location
#             print 'path==/ is: ' + str(path == self.root.location)
#             print 'before'
            if path == self.root.location:
#                 print 'returning root'
                return self.root
#             print 'after'
            currentEntry = self.root
#             print 'currentDir in findentry is: '+currentDir
#             print 'targetDir in findentry is: '+targetDir
            while currentDir != targetDir:
#                 print 'currentDir is: ' + currentDir
#                 print 'targetDir is: ' + targetDir
                foundEntry = False
                
                for key, entry in currentEntry.children.items():
#                     print 'entry location: ' + entry.location
                    if entry.is_dir:
#                         print 'entry is a dir'
                        if self.is_sub_dir(entry.location, targetDir) or entry.location==targetDir:
                            currentEntry = entry
                            foundEntry = True
                            break
                if foundEntry == False:
                    return None
                currentDir = currentEntry.location
#             print 'target in findentry: '+path
#             print 'final currentDir is: ' + currentDir
            if path == currentDir:
                
                return currentEntry
            else:
                targetEntry = None
                for key, entry in currentEntry.children.items():
                    if entry.location == path:
                        targetEntry = entry
                        break
                return targetEntry
            
        else:
            return None
        

    
    def readdir(self, path, offset):
        direntries = ['.', '..']
        entry = self.findentry(path)
        if entry != None:
            for key, item in entry.children.items():
                direntries.append(os.path.basename(item.location))
        for e in direntries:
            yield fuse.Direntry(e);
            
    def open(self, path, flags):
#         print 'opening '+path
        return 0
    
    def mknod(self, path, mode, dev):
        targetDir = os.path.dirname(path)
#         if os.path.isdir(path):
#             targetDir = os.path.dirname(path)
        entry = self.findentry(targetDir)
        if entry != None:
            entry.add_file(path, None, 0)
            return 0
        else:
            return -errno.ENOENT
        
    def unlink(self, path):
#         print "unlinking: "+path
        targetDir = os.path.dirname(path)
#         if os.path.isdir(path):
#             targetDir = os.path.dirname(path)
        entry = self.findentry(targetDir)
        if entry != None:
            return entry.remove_file(path)
        else:
            return -errno.ENOENT
        
    def write(self, path, buf, offset):
        """
        TODO: support offset into data
        """
#         if not os.path.isfile(path):
#             return -errno.ENOENT
#         else:
#         print 'writing: '+path
#         print 'data to write: '+buf
        entry = self.findentry(path)
        if entry is None:
            return -errno.ENOENT
        else:
            entry.size = len(buf)
            entry.data = buf
            return len(buf)
        
    def read(self, path, size, offset):
        """
        TODO: support offset into data
        """
#         if not os.path.isfile(path):
#             return -errno.ENOENT
#         else:
#         print 'reading '+path
        entry = self.findentry(path)
        if entry is None:
            return -errno.ENOENT
        else:
            return entry.data
        
    def release(self, path, flags):
        return 0
    
    def truncate(self, path, size):
        return 0
    
    def utime(self, path, times):
        return 0
    
    def mkdir(self, path, mode):
#         print 'mkdir: '+path
        targetDir = os.path.dirname(path)
        if os.path.isfile(path):
            return -errno.ENOENT
        else:
            entry = self.findentry(targetDir)
            if path in entry.children:
                return -errno.ENOENT
            else:
                entry.add_sub_directoy(path)
                return 0
        return 0
    
    def rmdir(self, path):
#         print "removing: "+path
        targetDir = os.path.dirname(path)
#         if os.path.isfile(path):
#             return -errno.ENOENT
#         else:
        entry = self.findentry(targetDir)
        if entry is None:
            return -errno.ENOENT
        elif path in entry.children:
            del entry.children[path]
            return 0
        return 0
        
    def rename(self, pathfrom, pathto):
#         print "renaming: "+pathfrom +" to "+pathto
        targetDir = os.path.dirname(pathfrom)
        newTargetDir = os.path.dirname(pathto)
        if targetDir != newTargetDir:
            return -errno.ENOENT
        else:
            parentEntry = self.findentry(targetDir)
            entry = self.findentry(pathfrom)
            entry.location = pathto
            parentEntry.children[pathto] = entry
            del parentEntry.children[pathfrom]
        return 0
    
    def fsync(self, path, isfsyncfile):
        return 0
       
# def main(root):
#     fuse.Fuse(my_fs(root), root, foreground=True)
 
if __name__ == '__main__':
    fs = my_fs()
    fs.parse(errex=1)
    fs.main()
#     main(sys.argv[1])