
import subprocess

class NameSpace:
    def __init__(self, name):
        self.name = name

    def add(self):
        return subprocess.check_output(['sudo', 'ip', 'netns', 'add', self.name])

    def delete(self):
        return subprocess.check_output(['sudo', 'ip', 'netns', 'delete', self.name])

    def list(self):
        return [ns for ns in subprocess.check_output(['ip', 'netns', 'list', self.name]).split('\n') if ns != '']

    def execute(self, command, use_sudo = False):
        if use_sudo:
            sudo = 'sudo'
        else:
            sudo = ''
        if isinstance(command, str):
            return subprocess.check_output([sudo, 'ip', 'netns', 'exec', self.name] + command.split() )
        if isinstance(command, list):
            return subprocess.check_output([sudo, 'ip', 'netns', 'exec', self.name] + command )

    def safelyAdd(self, force=False):
        """
        Add only if namespace doesn't already exist
        :param force: Boolean- if True, delete existing namespace of same name and add a new one.
        :return: None
        """
        if self.name in self.list():
            if force:
                print self.delete()
            else:
                return
        return self.add()

    def safelyDelete(self):
        if self.name in self.list():
            return self.delete()