import React from 'react';
import { FileAdapterConfig, SftpAdapterConfig, EmailAdapterConfig } from './config';

interface AdapterConfigFieldsProps {
  type: 'FILE' | 'SFTP' | 'EMAIL';
  direction: 'SENDER' | 'RECEIVER';
  control: any;
  register: any;
  errors: any;
  setValue: any;
  sshKeysData?: any[];
}

const AdapterConfigFields: React.FC<AdapterConfigFieldsProps> = ({
  type,
  direction,
  control,
  register,
  errors,
  setValue,
  sshKeysData = []
}) => {
  switch (type) {
    case 'FILE':
      return (
        <FileAdapterConfig
          direction={direction}
          control={control}
          register={register}
          errors={errors}
        />
      );
    case 'SFTP':
      return (
        <SftpAdapterConfig
          direction={direction}
          control={control}
          register={register}
          errors={errors}
          sshKeysData={sshKeysData}
        />
      );
    case 'EMAIL':
      return (
        <EmailAdapterConfig
          direction={direction}
          control={control}
          register={register}
          errors={errors}
          setValue={setValue}
        />
      );
    default:
      return null;
  }
};

export default AdapterConfigFields;
