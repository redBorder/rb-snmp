Name:     rb-snmp
Version:  %{__version}
Release:  %{__release}%{?dist}

License:  GNU AGPLv3
URL:  https://github.com/redBorder/rb-snmp
Source0: %{name}-%{version}.tar.gz

BuildRequires: maven java-devel

Summary: redborder snmp service 
Group: Services/Monitoring
Requires: java

%description
%{summary}

%prep
%setup -qn %{name}-%{version}

%build
mvn clean package

%install
mkdir -p %{buildroot}/usr/lib/%{name}
install -D -m 644 target/rb-snmp-*-selfcontained.jar %{buildroot}/usr/lib/%{name}
mv %{buildroot}/usr/lib/%{name}/rb-snmp-*-selfcontained.jar %{buildroot}/usr/lib/%{name}/rb-snmp.jar
install -D -m 644 src/main/resources/config_example.yml %{buildroot}/etc/%{name}/config_example.yml
install -D -m 644 rb-snmp.service %{buildroot}/usr/lib/systemd/system/rb-snmp.service

%clean
rm -rf %{buildroot}

%pre
getent group %{name} >/dev/null || groupadd -r %{name}
getent passwd %{name} >/dev/null || \
    useradd -r -g %{name} -d / -s /sbin/nologin \
    -c "User of %{name} service" %{name}
exit 0

%post -p /sbin/ldconfig
%postun -p /sbin/ldconfig

%files
%defattr(644,root,root)
/usr/lib/%{name}
/etc/%{name}/config_example.yml
/usr/lib/systemd/system/rb-snmp.service

%changelog
* Wed Jun 15 2016 Carlos J. Mateos  <cjmateos@redborder.com> - 1.0.0-1
- first spec version
